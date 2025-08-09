import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../services/battery_service.dart';
import '../services/app_launcher_service.dart';
import '../services/settings_service.dart';

class AutoLaunchController extends ChangeNotifier {
  final BatteryService _batteryService = BatteryService();
  final AppLauncherService _appLauncherService = AppLauncherService();
  final SettingsService _settingsService = SettingsService();

  bool _isInitialized = false;
  bool _isConnected = false;
  bool _isCharging = false;
  int _batteryLevel = 0;

  bool get isInitialized => _isInitialized;
  bool get isConnected => _isConnected;
  bool get isCharging => _isCharging;
  int get batteryLevel => _batteryLevel;
  bool get isServiceActive => _settingsService.serviceEnabled;
  String? get targetApp => _settingsService.targetApp;

  Future<void> initialize() async {
    try {
      // 서비스들 초기화
      await _batteryService.initialize();
      await _settingsService.initialize();

      // 배터리 서비스 리스너 설정
      _batteryService.addListener(_onBatteryStateChanged);

      // 설정된 대상 앱이 있으면 앱 런처에만 설정 (서비스 활성/비활성은 기존 상태 유지)
      if (_settingsService.targetApp != null) {
        await _appLauncherService.setTargetApp(_settingsService.targetApp!);
      }

      // 초기화 시에는 항상 앱 실행 상태를 false로 설정
      // serviceEnabled는 자동 실행 기능 활성화 여부이고,
      // isServiceActive는 현재 앱 실행 상태를 나타냄
      _appLauncherService.setServiceActive(false);

      // 초기화 시 저장된 서비스 상태에 맞춰 백그라운드 서비스 동기화
      if (_settingsService.serviceEnabled) {
        await _startBackgroundService();
      } else {
        await _stopBackgroundService();
      }

      // 초기 배터리 상태 동기화
      _isConnected = _batteryService.isConnected;
      _isCharging = _batteryService.isCharging;
      _batteryLevel = _batteryService.batteryLevel;

      _isInitialized = true;
      notifyListeners();

      if (kDebugMode) {
        print('AutoLaunch 컨트롤러 초기화 완료');
        print(
          '초기 상태 - 충전중: $_isCharging, 연결됨: $_isConnected, 배터리: $_batteryLevel%',
        );
        print('서비스 활성화(저장값): ${_settingsService.serviceEnabled}');
      }
    } catch (e) {
      if (kDebugMode) {
        print('AutoLaunch 컨트롤러 초기화 오류: $e');
      }
    }
  }

  void _onBatteryStateChanged() {
    final wasConnected = _isConnected;
    final wasCharging = _isCharging;

    _isConnected = _batteryService.isConnected;
    _isCharging = _batteryService.isCharging;
    _batteryLevel = _batteryService.batteryLevel;

    // 서비스가 활성화되어 있고 대상 앱이 설정되어 있을 때만 처리
    if (_settingsService.serviceEnabled && _settingsService.targetApp != null) {
      _handleChargingStateChange(wasConnected, wasCharging);
    }

    notifyListeners();
  }

  void _handleChargingStateChange(bool wasConnected, bool wasCharging) {
    // 충전 연결 시
    if (!wasConnected && _isConnected) {
      _handleChargingConnected();
    }
    // 충전 해제 시
    else if (wasConnected && !_isConnected) {
      _handleChargingDisconnected();
    }
  }

  Future<void> _handleChargingConnected() async {
    if (kDebugMode) {
      print('충전 연결 감지 - 실행은 네이티브 경로에서 처리(5초 후 UI)');
    }

    // 대상 앱이 설정되어 있는지 확인
    if (_settingsService.targetApp == null) {
      if (kDebugMode) {
        print('대상 앱이 설정되지 않음');
      }
      return;
    }

    // 연결 해제 타이머 취소
    _appLauncherService.cancelDisconnectTimer();

    // 앱 실행은 네이티브(BroadcastReceiver/Service)에서 5초 후 안내 UI와 함께 처리
    // 여기서는 상태만 동기화
    _appLauncherService.setServiceActive(false);
  }

  Future<void> _handleChargingDisconnected() async {
    if (kDebugMode) {
      print('충전 해제 감지 - 앱 종료 타이머 시작');
    }

    // 설정된 지연 시간만큼 대기 후 앱 종료
    _appLauncherService.startDisconnectTimer();
  }

  Future<void> setTargetApp(String? appPackage) async {
    await _settingsService.setTargetApp(appPackage);

    if (appPackage != null) {
      await _appLauncherService.setTargetApp(appPackage);
      // 앱을 선택해도 자동 활성화하지 않음 (사용자 의사 보존)
      _appLauncherService.setServiceActive(false);
    } else {
      // 앱 선택이 해제되면 서비스 비활성화
      await _settingsService.setServiceEnabled(false);
      _appLauncherService.cancelDisconnectTimer();
      _appLauncherService.setServiceActive(false);
    }

    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
  }

  Future<void> setServiceEnabled(bool enabled) async {
    await _settingsService.setServiceEnabled(enabled);

    if (enabled) {
      // 서비스 활성화 시 백그라운드 서비스 시작
      await _startBackgroundService();
      // 앱 실행 상태는 false로 유지 (충전 연결 시에만 실행)
      _appLauncherService.setServiceActive(false);

      // 네이티브로 현재 충전 중이면 대기 UI(5초) 트리거
      const MethodChannel channel = MethodChannel(
        'com.autolaunch.app/permissions',
      );
      try {
        await channel.invokeMethod('triggerLaunchIfCharging');
      } catch (_) {}
    } else {
      // 서비스 비활성화 시 백그라운드 서비스 중지
      await _stopBackgroundService();
      // 타이머 취소하고 앱 실행 상태도 false로 설정
      _appLauncherService.cancelDisconnectTimer();
      _appLauncherService.setServiceActive(false);
    }

    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
  }

  Future<void> _startBackgroundService() async {
    try {
      if (kDebugMode) {
        print('🔥🔥🔥 백그라운드 서비스 즉시 시작 🔥🔥🔥');
      }
      // 네이티브 백그라운드 서비스 시작 (MethodChannel 사용)
      const MethodChannel channel = MethodChannel(
        'com.autolaunch.app/permissions',
      );
      await channel.invokeMethod('startBackgroundService');
    } catch (e) {
      if (kDebugMode) {
        print('백그라운드 서비스 시작 오류: $e');
      }
    }
  }

  Future<void> startBackgroundService() async {
    await _startBackgroundService();
  }

  Future<void> stopBackgroundService() async {
    try {
      if (kDebugMode) {
        print('🔥🔥🔥 백그라운드 서비스 중지 🔥🔥🔥');
      }
      // 네이티브 백그라운드 서비스 중지 (MethodChannel 사용)
      const MethodChannel channel = MethodChannel(
        'com.autolaunch.app/permissions',
      );
      await channel.invokeMethod('stopBackgroundService');
    } catch (e) {
      if (kDebugMode) {
        print('백그라운드 서비스 중지 오류: $e');
      }
    }
  }

  Future<void> _stopBackgroundService() async {
    await stopBackgroundService();
  }

  @override
  void dispose() {
    _batteryService.removeListener(_onBatteryStateChanged);
    _batteryService.dispose();
    _appLauncherService.dispose();
    _settingsService.dispose();
    super.dispose();
  }
}
