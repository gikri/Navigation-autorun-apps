import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

class AppLauncherService extends ChangeNotifier {
  bool _isServiceActive = false;
  String? _targetAppPackage;
  Timer? _disconnectTimer;

  bool get isServiceActive => _isServiceActive;
  String? get targetAppPackage => _targetAppPackage;

  // 주요 네비게이션 앱들의 URL 스킴 매핑 (T맵, 카카오네비, 카카오맵, 네이버맵)
  static const Map<String, String> _appSchemes = {
    'com.skt.tmap.ku': 'tmap://',
    'com.kakao.navi': 'kakaonavi://',
    'net.daum.android.map': 'daummaps://',
    'com.nhn.android.nmap': 'nmap://',
  };

  Future<void> setTargetApp(String packageName) async {
    _targetAppPackage = packageName;
    notifyListeners();

    if (kDebugMode) {
      print('대상 앱 설정: $packageName');
    }
  }

  Future<bool> launchTargetApp() async {
    if (_targetAppPackage == null) {
      if (kDebugMode) {
        print('대상 앱이 설정되지 않음');
      }
      return false;
    }

    try {
      // URL 스킴으로 앱 실행 시도
      final scheme = _appSchemes[_targetAppPackage!];
      if (scheme != null) {
        final uri = Uri.parse(scheme);
        if (await canLaunchUrl(uri)) {
          await launchUrl(uri, mode: LaunchMode.externalApplication);

          if (kDebugMode) {
            print('앱 실행 성공 (URL 스킴): $_targetAppPackage');
          }
          return true;
        }
      }

      // 대안 1: 패키지 URL로 실행 시도
      final packageUrl = 'package:$_targetAppPackage';
      final packageUri = Uri.parse(packageUrl);
      if (await canLaunchUrl(packageUri)) {
        await launchUrl(packageUri, mode: LaunchMode.externalApplication);

        if (kDebugMode) {
          print('앱 실행 성공 (패키지 URL): $_targetAppPackage');
        }
        return true;
      }

      // 대안 2: Android Intent로 실행 시도
      final intentUrl = 'intent://launch#Intent;package=$_targetAppPackage;end';
      final intentUri = Uri.parse(intentUrl);
      if (await canLaunchUrl(intentUri)) {
        await launchUrl(intentUri, mode: LaunchMode.externalApplication);

        if (kDebugMode) {
          print('앱 실행 성공 (Intent): $_targetAppPackage');
        }
        return true;
      }

      // 대안 3: 플레이스토어로 이동
      final storeUrl = 'market://details?id=$_targetAppPackage';
      final storeUri = Uri.parse(storeUrl);
      if (await canLaunchUrl(storeUri)) {
        await launchUrl(storeUri, mode: LaunchMode.externalApplication);

        if (kDebugMode) {
          print('플레이스토어로 이동: $_targetAppPackage');
        }
        return true;
      }
    } catch (e) {
      if (kDebugMode) {
        print('앱 실행 오류: $e');
      }
    }

    return false;
  }

  Future<void> closeTargetApp() async {
    if (_targetAppPackage == null) {
      if (kDebugMode) {
        print('대상 앱이 설정되지 않음');
      }
      return;
    }

    try {
      // Android에서 앱 종료를 위한 Intent 전송
      final closeIntent =
          'intent://close#Intent;package=$_targetAppPackage;end';
      final closeUri = Uri.parse(closeIntent);

      // 앱 종료 시도
      try {
        await launchUrl(closeUri, mode: LaunchMode.externalApplication);
        if (kDebugMode) {
          print('앱 종료 신호 전송: $_targetAppPackage');
        }
      } catch (e) {
        if (kDebugMode) {
          print('앱 종료 신호 전송 실패: $e');
        }
      }

      if (kDebugMode) {
        print('대상 앱 종료 요청 완료: $_targetAppPackage');
      }
    } catch (e) {
      if (kDebugMode) {
        print('앱 종료 처리 오류: $e');
      }
    }
  }

  void startDisconnectTimer() {
    _disconnectTimer?.cancel();
    _disconnectTimer = Timer(const Duration(seconds: 1), () async {
      // 2초에서 1초로 단축
      await closeTargetApp();
      setServiceActive(false);
    });

    if (kDebugMode) {
      print('연결 해제 타이머 시작 (1초)'); // 로그 메시지 업데이트
    }
  }

  void cancelDisconnectTimer() {
    _disconnectTimer?.cancel();

    if (kDebugMode) {
      print('연결 해제 타이머 취소');
    }
  }

  void setServiceActive(bool active) {
    _isServiceActive = active;
    notifyListeners();

    if (kDebugMode) {
      print('서비스 상태 변경: $active');
    }
  }

  @override
  void dispose() {
    _disconnectTimer?.cancel();
    super.dispose();
  }
}
