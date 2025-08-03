import 'dart:async';
import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/foundation.dart';

class BatteryService extends ChangeNotifier {
  final Battery _battery = Battery();
  StreamSubscription<BatteryState>? _batteryStateSubscription;
  Timer? _batteryLevelTimer;

  bool _isCharging = false;
  bool _isConnected = false;
  int _batteryLevel = 0;
  bool _isInitialized = false;

  bool get isCharging => _isCharging;
  bool get isConnected => _isConnected;
  int get batteryLevel => _batteryLevel;
  bool get isInitialized => _isInitialized;

  Future<void> initialize() async {
    try {
      // 초기 배터리 상태 가져오기
      await _updateBatteryStatus();

      // 배터리 상태 변화 감지
      _batteryStateSubscription = _battery.onBatteryStateChanged.listen(
        (BatteryState state) {
          _handleBatteryStateChange(state);
        },
        onError: (error) {
          if (kDebugMode) {
            print('배터리 상태 스트림 오류: $error');
          }
        },
      );

      // 배터리 레벨 주기적 업데이트
      _batteryLevelTimer = Timer.periodic(const Duration(seconds: 10), (
        timer,
      ) async {
        await _updateBatteryLevel();
      });

      _isInitialized = true;
      notifyListeners();

      if (kDebugMode) {
        print(
          'BatteryService 초기화 완료 - 충전중: $_isCharging, 연결됨: $_isConnected, 레벨: $_batteryLevel%',
        );
      }
    } catch (e) {
      if (kDebugMode) {
        print('BatteryService 초기화 오류: $e');
      }
    }
  }

  Future<void> _updateBatteryStatus() async {
    try {
      final level = await _battery.batteryLevel;
      final state = await _battery.batteryState;

      _batteryLevel = level;
      _updateConnectionState(state);

      if (kDebugMode) {
        print('배터리 상태 업데이트: 레벨=$_batteryLevel%, 상태=$state');
      }
    } catch (e) {
      if (kDebugMode) {
        print('배터리 상태 업데이트 오류: $e');
      }
    }
  }

  Future<void> _updateBatteryLevel() async {
    try {
      final level = await _battery.batteryLevel;
      if (level != _batteryLevel) {
        _batteryLevel = level;
        notifyListeners();

        if (kDebugMode) {
          print('배터리 레벨 업데이트: $_batteryLevel%');
        }
      }
    } catch (e) {
      if (kDebugMode) {
        print('배터리 레벨 확인 오류: $e');
      }
    }
  }

  void _handleBatteryStateChange(BatteryState state) {
    bool wasCharging = _isCharging;
    bool wasConnected = _isConnected;

    _updateConnectionState(state);

    // 상태 변화가 있을 때만 알림
    if (wasCharging != _isCharging || wasConnected != _isConnected) {
      notifyListeners();

      if (kDebugMode) {
        print('배터리 상태 변화: $state -> 충전중=$_isCharging, 연결됨=$_isConnected');
      }
    }
  }

  void _updateConnectionState(BatteryState state) {
    switch (state) {
      case BatteryState.charging:
        _isCharging = true;
        _isConnected = true;
        break;
      case BatteryState.full:
        _isCharging = false;
        _isConnected = true;
        break;
      case BatteryState.discharging:
        _isCharging = false;
        _isConnected = false;
        break;
      case BatteryState.connectedNotCharging:
        _isCharging = false;
        _isConnected = true;
        break;
      case BatteryState.unknown:
        // 알 수 없는 상태에서는 이전 상태를 유지
        if (kDebugMode) {
          print('배터리 상태 알 수 없음 - 이전 상태 유지');
        }
        break;
    }
  }

  Future<bool> isChargingConnected() async {
    try {
      final state = await _battery.batteryState;
      return state == BatteryState.charging ||
          state == BatteryState.full ||
          state == BatteryState.connectedNotCharging;
    } catch (e) {
      if (kDebugMode) {
        print('충전 상태 확인 오류: $e');
      }
      return false;
    }
  }

  @override
  void dispose() {
    _batteryStateSubscription?.cancel();
    _batteryLevelTimer?.cancel();
    super.dispose();
  }
}
