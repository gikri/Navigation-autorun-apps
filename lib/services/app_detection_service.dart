import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class AppDetectionService {
  static const MethodChannel _channel = MethodChannel(
    'com.autolaunch.app/app_detection',
  );

  // 지원하는 네비게이션 앱들 (타겟 쿼리 방식으로 확장)
  static const Map<String, Map<String, String>> supportedApps = {
    'com.skt.tmap.ku': {
      'name': 'T맵',
      'description': 'SK텔레콤 내비게이션',
      'urlScheme': 'tmap://',
    },
    'com.kakao.navi': {
      'name': '카카오네비',
      'description': '카카오 전용 내비게이션',
      'urlScheme': 'kakaonavi://',
    },
    'net.daum.android.map': {
      'name': '카카오맵',
      'description': '카카오 지도 및 길찾기',
      'urlScheme': 'daummaps://',
    },
    'com.nhn.android.nmap': {
      'name': '네이버지도',
      'description': '네이버 지도 및 내비게이션',
      'urlScheme': 'nmap://',
    },
    'com.google.android.apps.maps': {
      'name': '구글맵',
      'description': '구글 지도 및 내비게이션',
      'urlScheme': 'googlemaps://',
    },
    'com.waze': {
      'name': 'Waze',
      'description': 'Waze 내비게이션',
      'urlScheme': 'waze://',
    },
  };

  /// 설치된 네비게이션 앱 목록을 가져옵니다 (타겟 쿼리 방식)
  static Future<List<Map<String, String>>> getInstalledNavigationApps() async {
    try {
      final List<Map<String, String>> result = [];

      // 각 지원 앱에 대해 개별적으로 설치 여부 확인
      for (String packageName in supportedApps.keys) {
        final bool isInstalled = await isAppInstalled(packageName);

        if (isInstalled) {
          final appInfo = supportedApps[packageName]!;
          result.add({
            'packageName': packageName,
            'name': appInfo['name']!,
            'description': appInfo['description']!,
            'urlScheme': appInfo['urlScheme']!,
          });
        }
      }

      if (kDebugMode) {
        print('설치된 네비게이션 앱: $result');
      }

      return result;
    } catch (e) {
      if (kDebugMode) {
        print('설치된 앱 확인 오류: $e');
      }
      return [];
    }
  }

  /// 특정 앱이 설치되어 있는지 확인합니다 (타겟 쿼리 방식)
  static Future<bool> isAppInstalled(String packageName) async {
    try {
      final bool isInstalled = await _channel.invokeMethod('isAppInstalled', {
        'packageName': packageName,
      });

      if (kDebugMode) {
        print('$packageName 설치 여부: $isInstalled');
      }

      return isInstalled;
    } catch (e) {
      if (kDebugMode) {
        print('앱 설치 확인 오류: $e');
      }
      return false;
    }
  }

  /// 앱의 상세 정보를 가져옵니다
  static Future<Map<String, String>?> getAppInfo(String packageName) async {
    try {
      final Map<dynamic, dynamic> appInfo = await _channel.invokeMethod(
        'getAppInfo',
        {'packageName': packageName},
      );

      final result = {
        'packageName': packageName,
        'appName': appInfo['appName'] as String,
        'versionName': appInfo['versionName'] as String,
        'versionCode': appInfo['versionCode'].toString(),
      };

      if (kDebugMode) {
        print('앱 정보: $result');
      }

      return result;
    } catch (e) {
      if (kDebugMode) {
        print('앱 정보 가져오기 오류: $e');
      }
      return null;
    }
  }

  /// 모든 지원 앱의 설치 상태를 확인합니다
  static Future<Map<String, bool>> checkAllAppsInstallation() async {
    final Map<String, bool> installationStatus = {};

    for (String packageName in supportedApps.keys) {
      installationStatus[packageName] = await isAppInstalled(packageName);
    }

    if (kDebugMode) {
      print('모든 앱 설치 상태: $installationStatus');
    }

    return installationStatus;
  }

  /// 설치된 앱 중에서 추천 앱을 반환합니다
  static Future<String?> getRecommendedApp() async {
    final installedApps = await getInstalledNavigationApps();

    if (installedApps.isEmpty) {
      return null;
    }

    // 우선순위: T맵 > 카카오네비 > 카카오맵 > 네이버지도 > 구글맵 > Waze
    const priority = [
      'com.skt.tmap.ku',
      'com.kakao.navi',
      'net.daum.android.map',
      'com.nhn.android.nmap',
      'com.google.android.apps.maps',
      'com.waze',
    ];

    for (String packageName in priority) {
      if (installedApps.any((app) => app['packageName'] == packageName)) {
        if (kDebugMode) {
          print('추천 앱: ${supportedApps[packageName]!['name']}');
        }
        return packageName;
      }
    }

    // 우선순위에 없는 경우 첫 번째 앱 반환
    return installedApps.first['packageName'];
  }

  /// 지원하는 모든 앱 목록을 반환합니다 (설치 여부와 함께)
  static Future<List<Map<String, dynamic>>> getAllSupportedApps() async {
    final List<Map<String, dynamic>> result = [];

    for (String packageName in supportedApps.keys) {
      final appInfo = supportedApps[packageName]!;
      final bool isInstalled = await isAppInstalled(packageName);

      result.add({
        'packageName': packageName,
        'name': appInfo['name']!,
        'description': appInfo['description']!,
        'urlScheme': appInfo['urlScheme']!,
        'isInstalled': isInstalled,
      });
    }

    if (kDebugMode) {
      print('지원 앱 목록: $result');
    }

    return result;
  }
}
