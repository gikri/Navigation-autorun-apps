import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PermissionService {
  static const MethodChannel _channel = MethodChannel(
    'com.autolaunch.app/permissions',
  );

  static Future<void> requestAllPermissions() async {
    try {
      await _channel.invokeMethod('requestPermissions');
      if (kDebugMode) {
        print('권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('권한 요청 오류: $e');
      }
    }
  }

  static Future<Map<String, bool>> checkPermissions() async {
    try {
      final result = await _channel.invokeMethod('checkPermissions');
      final permissions = Map<String, bool>.from(result);

      if (kDebugMode) {
        print('권한 상태 확인: $permissions');
      }

      return permissions;
    } catch (e) {
      if (kDebugMode) {
        print('권한 상태 확인 오류: $e');
      }
      return {};
    }
  }

  static Future<void> requestBatteryOptimization() async {
    try {
      await _channel.invokeMethod('openBatteryOptimizationSettings');
      if (kDebugMode) {
        print('배터리 최적화 설정 열기 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('배터리 최적화 설정 열기 오류: $e');
      }
    }
  }

  static Future<void> requestWriteSettings() async {
    try {
      await _channel.invokeMethod('requestWriteSettings');
      if (kDebugMode) {
        print('시스템 설정 변경 권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('시스템 설정 변경 권한 요청 오류: $e');
      }
    }
  }

  static Future<void> requestAccessibilityService() async {
    try {
      await _channel.invokeMethod('requestAccessibilityService');
      if (kDebugMode) {
        print('접근성 서비스 권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('접근성 서비스 권한 요청 오류: $e');
      }
    }
  }



  static Future<void> requestNotificationPermission() async {
    try {
      await _channel.invokeMethod('requestNotificationPermission');
      if (kDebugMode) {
        print('알림 권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('알림 권한 요청 오류: $e');
      }
    }
  }

  static Future<void> requestSystemAlertWindow() async {
    try {
      await _channel.invokeMethod('requestSystemAlertWindow');
      if (kDebugMode) {
        print('다른 앱 위에 그리기 권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('다른 앱 위에 그리기 권한 요청 오류: $e');
      }
    }
  }

  static void setupCloseAppListener(Function() onCloseApp) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'closeApp') {
        if (kDebugMode) {
          print('앱 종료 신호 수신');
        }
        onCloseApp();
      }
    });
  }

  /// 배터리 최적화 화이트리스트 강제 추가
  static Future<void> forceBatteryOptimizationWhitelist() async {
    try {
      await _channel.invokeMethod('forceBatteryOptimization');
      if (kDebugMode) {
        print('배터리 최적화 강제 화이트리스트 추가 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('배터리 최적화 강제 화이트리스트 추가 오류: $e');
      }
    }
  }

  /// 백그라운드 실행을 위한 중요한 권한들 체크 및 자동 요청
  static Future<Map<String, dynamic>>
  checkAndRequestCriticalPermissions() async {
    try {
      final result = await _channel.invokeMethod('checkCriticalPermissions');
      final response = Map<String, dynamic>.from(result);

      if (kDebugMode) {
        print('중요 권한 체크 결과: $response');
      }

      return response;
    } catch (e) {
      if (kDebugMode) {
        print('중요 권한 체크 오류: $e');
      }
      return {
        'hasIssues': true,
        'issues': ['unknown_error'],
        'message': '권한 체크 중 오류가 발생했습니다: $e',
      };
    }
  }

  /// 잠금 상태에서도 작동하는지 확인하는 헬퍼 메서드 (레퍼런스 앱 기준 4가지 권한)
  static Future<bool> isReadyForBackgroundOperation() async {
    try {
      final permissions = await checkPermissions();
      final criticalCheck = await checkAndRequestCriticalPermissions();

      // 레퍼런스 앱 기준 4가지 핵심 권한 확인
      final writeSettings = permissions['write_settings'] ?? false;
      final batteryOptimized = permissions['battery_optimization'] ?? false;
      final systemAlert = permissions['system_alert_window'] ?? false;
      final accessibilityService =
          permissions['accessibility_service'] ?? false;

      final hasNoCriticalIssues = !(criticalCheck['hasIssues'] ?? true);
      final grantedCount = criticalCheck['grantedCount'] ?? 0;
      final totalCount = criticalCheck['totalCount'] ?? 4;

      // 4가지 권한이 모두 허용되어야 완전히 준비됨
      final isReady =
          writeSettings &&
          batteryOptimized &&
          systemAlert &&
          accessibilityService &&
          hasNoCriticalIssues;

      if (kDebugMode) {
        print('잠금 상태 작업 준비 상태: $isReady ($grantedCount/$totalCount)');
        print('- 시스템 설정 수정: $writeSettings');
        print('- 배터리 제한 해제: $batteryOptimized');
        print('- 다른앱 위에 그리기: $systemAlert');
        print('- 접근성 서비스: $accessibilityService');
        print('- 중요 문제 없음: $hasNoCriticalIssues');
      }

      return isReady;
    } catch (e) {
      if (kDebugMode) {
        print('잠금 상태 작업 준비 상태 확인 오류: $e');
      }
      return false;
    }
  }

  /// 기기 제조사 정보 확인 (샤오미, 화웨이 등)
  static Future<Map<String, dynamic>> getDeviceVendorInfo() async {
    try {
      final result = await _channel.invokeMethod('checkDeviceVendor');
      final deviceInfo = Map<String, dynamic>.from(result);

      if (kDebugMode) {
        print('기기 정보: $deviceInfo');
      }

      return deviceInfo;
    } catch (e) {
      if (kDebugMode) {
        print('기기 정보 확인 오류: $e');
      }
      return {
        'manufacturer': 'UNKNOWN',
        'model': 'UNKNOWN',
        'brand': 'UNKNOWN',
        'isXiaomi': false,
        'isHuawei': false,
        'isOppo': false,
        'isSamsung': false,
        'isVivo': false,
        'needsAutostartPermission': false,
        'needsPowerManagerPermission': false,
        'vendorName': '알 수 없음',
      };
    }
  }

  /// 샤오미 MIUI 자동 실행 권한 요청
  static Future<void> requestXiaomiAutostartPermission() async {
    try {
      await _channel.invokeMethod('requestXiaomiAutostart');
      if (kDebugMode) {
        print('샤오미 자동 실행 권한 요청 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('샤오미 자동 실행 권한 요청 오류: $e');
      }
    }
  }

  /// 제조사별 추가 권한이 필요한지 확인
  static Future<bool> needsVendorSpecificPermissions() async {
    try {
      final deviceInfo = await getDeviceVendorInfo();
      final needsAutostart = deviceInfo['needsAutostartPermission'] ?? false;
      final needsPowerManager =
          deviceInfo['needsPowerManagerPermission'] ?? false;

      return needsAutostart || needsPowerManager;
    } catch (e) {
      if (kDebugMode) {
        print('제조사별 권한 필요 여부 확인 오류: $e');
      }
      return false;
    }
  }

  /// 확장된 백그라운드 실행 준비 상태 확인 (제조사별 권한 포함)
  static Future<Map<String, dynamic>> getExtendedBackgroundReadiness() async {
    try {
      final permissions = await checkPermissions();
      final criticalCheck = await checkAndRequestCriticalPermissions();
      final deviceInfo = await getDeviceVendorInfo();

      // 기본 4가지 핵심 권한
      final writeSettings = permissions['write_settings'] ?? false;
      final batteryOptimized = permissions['battery_optimization'] ?? false;
      final systemAlert = permissions['system_alert_window'] ?? false;
      final accessibilityService =
          permissions['accessibility_service'] ?? false;

      final corePermissionsReady =
          writeSettings &&
          batteryOptimized &&
          systemAlert &&
          accessibilityService;

      // 제조사별 추가 권한
      final isXiaomi = deviceInfo['isXiaomi'] ?? false;
      final vendorName = deviceInfo['vendorName'] ?? '알 수 없음';

      var allPermissionsReady = corePermissionsReady;
      var vendorRequiredCount = 0;
      var vendorGrantedCount = 0;
      final vendorPermissions = <String, bool>{};

      if (isXiaomi) {
        vendorRequiredCount = 1; // 자동 실행 권한
        // 사용자가 수동으로 설정했다고 표시한 상태 확인
        final xiaomiAutostartCompleted = await _getXiaomiAutostartStatus();
        vendorPermissions['xiaomi_autostart'] = xiaomiAutostartCompleted;
        vendorGrantedCount = xiaomiAutostartCompleted ? 1 : 0;
      }

      final totalRequiredPermissions = 4 + vendorRequiredCount;
      final totalGrantedPermissions =
          (corePermissionsReady ? 4 : (criticalCheck['grantedCount'] ?? 0)) +
          vendorGrantedCount;

      return {
        'corePermissionsReady': corePermissionsReady,
        'allPermissionsReady':
            allPermissionsReady &&
            (vendorRequiredCount == 0 || vendorGrantedCount > 0),
        'coreGrantedCount': criticalCheck['grantedCount'] ?? 0,
        'coreTotalCount': 4,
        'vendorGrantedCount': vendorGrantedCount,
        'vendorRequiredCount': vendorRequiredCount,
        'totalGrantedCount': totalGrantedPermissions,
        'totalRequiredCount': totalRequiredPermissions,
        'deviceInfo': deviceInfo,
        'vendorName': vendorName,
        'vendorPermissions': vendorPermissions,
        'needsVendorPermissions': vendorRequiredCount > 0,
        'message': _getReadinessMessage(
          corePermissionsReady,
          vendorRequiredCount,
          vendorGrantedCount,
          vendorName,
        ),
      };
    } catch (e) {
      if (kDebugMode) {
        print('확장된 백그라운드 준비 상태 확인 오류: $e');
      }
      return {
        'corePermissionsReady': false,
        'allPermissionsReady': false,
        'error': e.toString(),
      };
    }
  }

  static String _getReadinessMessage(
    bool coreReady,
    int vendorRequired,
    int vendorGranted,
    String vendorName,
  ) {
    if (coreReady && vendorRequired == 0) {
      return '모든 권한이 설정되었습니다! 🎉';
    } else if (coreReady &&
        vendorRequired > 0 &&
        vendorGranted >= vendorRequired) {
      return '$vendorName 기기에 최적화된 모든 권한이 설정되었습니다! 🎉';
    } else if (coreReady && vendorRequired > 0) {
      return '핵심 권한은 완료! $vendorName 전용 권한을 추가로 설정하세요. 🔧';
    } else if (vendorRequired > 0) {
      return '기본 권한과 $vendorName 전용 권한이 필요합니다. ⚙️';
    } else {
      return '필수 권한 설정이 필요합니다. 🔑';
    }
  }

  /// 샤오미 자동실행 권한 설정 상태를 확인 (로컬 저장소 기반)
  static Future<bool> _getXiaomiAutostartStatus() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool('xiaomi_autostart_completed') ?? false;
    } catch (e) {
      if (kDebugMode) {
        print('샤오미 자동실행 권한 상태 확인 오류: $e');
      }
      return false;
    }
  }

  /// 사용자가 샤오미 자동실행 권한을 수동으로 설정했다고 표시
  static Future<void> markXiaomiAutostartAsCompleted() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('xiaomi_autostart_completed', true);
      if (kDebugMode) {
        print('샤오미 자동실행 권한 설정 완료로 표시됨');
      }
    } catch (e) {
      if (kDebugMode) {
        print('샤오미 자동실행 권한 설정 완료 표시 오류: $e');
      }
    }
  }

  /// 샤오미 자동실행 권한 설정 상태 초기화 (테스트/재설정용)
  static Future<void> resetXiaomiAutostartStatus() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('xiaomi_autostart_completed', false);
      if (kDebugMode) {
        print('샤오미 자동실행 권한 상태 초기화됨');
      }
    } catch (e) {
      if (kDebugMode) {
        print('샤오미 자동실행 권한 상태 초기화 오류: $e');
      }
    }
  }
}
