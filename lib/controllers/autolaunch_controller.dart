import 'dart:async';
import 'package:flutter/foundation.dart';
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
  int get delayTime => _settingsService.delayTime;

  Future<void> initialize() async {
    try {
      // 서비스들 초기화
      await _batteryService.initialize();
      await _settingsService.initialize();

      // 배터리 서비스 리스너 설정
      _batteryService.addListener(_onBatteryStateChanged);

      // 설정된 대상 앱이 있으면 앱 런처에 설정
      if (_settingsService.targetApp != null) {
        await _appLauncherService.setTargetApp(_settingsService.targetApp!);
        // 대상 앱이 설정되어 있으면 서비스 활성화
        await _settingsService.setServiceEnabled(true);
      } else {
        // 대상 앱이 없으면 서비스 비활성화
        await _settingsService.setServiceEnabled(false);
      }

      // 초기화 시에는 항상 앱 실행 상태를 false로 설정
      // serviceEnabled는 자동 실행 기능 활성화 여부이고,
      // isServiceActive는 현재 앱 실행 상태를 나타냄
      _appLauncherService.setServiceActive(false);

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
        print('서비스 활성화: ${_settingsService.serviceEnabled}');
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
      print('충전 연결 감지 - 앱 실행 시작');
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

    // 앱 실행
    final success = await _appLauncherService.launchTargetApp();

    if (success) {
      _appLauncherService.setServiceActive(true);

      if (kDebugMode) {
        print('앱 실행 성공');
      }
    } else {
      if (kDebugMode) {
        print('앱 실행 실패');
      }
    }
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
      // 앱이 선택되면 서비스만 활성화하고, 실행은 하지 않음
      await _settingsService.setServiceEnabled(true);
      // 앱 실행 상태는 false로 유지
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

  Future<void> setDelayTime(int seconds) async {
    await _settingsService.setDelayTime(seconds);
    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
  }

  Future<void> setBluetoothException(bool enabled) async {
    await _settingsService.setBluetoothException(enabled);
    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
  }

  Future<void> setBatteryOptimization(bool enabled) async {
    await _settingsService.setBatteryOptimization(enabled);
    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
  }

  Future<void> setServiceEnabled(bool enabled) async {
    await _settingsService.setServiceEnabled(enabled);

    if (enabled) {
      // 서비스 활성화 시 앱 실행 상태는 false로 유지 (충전 연결 시에만 실행)
      _appLauncherService.setServiceActive(false);
    } else {
      // 서비스 비활성화 시 타이머 취소하고 앱 실행 상태도 false로 설정
      _appLauncherService.cancelDisconnectTimer();
      _appLauncherService.setServiceActive(false);
    }

    // UI 실시간 업데이트를 위해 notifyListeners 호출
    notifyListeners();
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
