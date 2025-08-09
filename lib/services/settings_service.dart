import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsService extends ChangeNotifier {
  static const String _keyTargetApp = 'target_app';
  static const String _keyServiceEnabled = 'service_enabled';

  String? _targetApp;
  bool _serviceEnabled = false;

  String? get targetApp => _targetApp;
  bool get serviceEnabled => _serviceEnabled;

  Future<void> initialize() async {
    try {
      final prefs = await SharedPreferences.getInstance();

      _targetApp = prefs.getString(_keyTargetApp);
      _serviceEnabled = prefs.getBool(_keyServiceEnabled) ?? false;

      notifyListeners();

      if (kDebugMode) {
        print('설정 로드 완료');
      }
    } catch (e) {
      if (kDebugMode) {
        print('설정 로드 오류: $e');
      }
    }
  }

  Future<void> setTargetApp(String? appPackage) async {
    _targetApp = appPackage;
    await _saveString(_keyTargetApp, appPackage);
    notifyListeners();
  }



  Future<void> setServiceEnabled(bool enabled) async {
    _serviceEnabled = enabled;
    await _saveBool(_keyServiceEnabled, enabled);
    notifyListeners();
  }

  Future<void> _saveString(String key, String? value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (value != null) {
        await prefs.setString(key, value);
      } else {
        await prefs.remove(key);
      }
    } catch (e) {
      if (kDebugMode) {
        print('설정 저장 오류 ($key): $e');
      }
    }
  }

  Future<void> _saveInt(String key, int value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(key, value);
    } catch (e) {
      if (kDebugMode) {
        print('설정 저장 오류 ($key): $e');
      }
    }
  }

  Future<void> _saveBool(String key, bool value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(key, value);
    } catch (e) {
      if (kDebugMode) {
        print('설정 저장 오류 ($key): $e');
      }
    }
  }

  // 일반적인 내비게이션 앱 패키지명들 (T맵, 카카오네비, 카카오맵, 네이버맵)
  static const List<String> commonNavigationApps = [
    'com.skt.tmap.ku', // T맵
    'com.kakao.navi', // 카카오네비
    'net.daum.android.map', // 카카오맵
    'com.nhn.android.nmap', // 네이버맵
  ];

  static const List<String> commonAppNames = ['T맵', '카카오네비', '카카오맵', '네이버맵'];

  // 앱 패키지명과 이름 매핑
  static const Map<String, String> appNameMap = {
    'com.skt.tmap.ku': 'T맵',
    'com.kakao.navi': '카카오네비',
    'net.daum.android.map': '카카오맵',
    'com.nhn.android.nmap': '네이버맵',
  };

  // URL 스킴 매핑
  static const Map<String, String> appSchemeMap = {
    'com.skt.tmap.ku': 'tmap://',
    'com.kakao.navi': 'kakaonavi://',
    'net.daum.android.map': 'daummaps://',
    'com.nhn.android.nmap': 'nmap://',
  };

  static String getAppName(String packageName) {
    return appNameMap[packageName] ?? packageName;
  }

  static String? getPackageName(String appName) {
    for (var entry in appNameMap.entries) {
      if (entry.value == appName) {
        return entry.key;
      }
    }
    return null;
  }

  void dispose() {
    // 설정 서비스는 일반적으로 앱 생명주기 동안 유지되므로
    // 특별한 정리 작업은 필요하지 않음
  }
}
