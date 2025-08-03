import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter/services.dart';
import 'controllers/autolaunch_controller.dart';
import 'screens/main_screen.dart';
import 'screens/permissions_screen.dart';
import 'services/permission_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const MethodChannel _channel = MethodChannel(
    'com.autolaunch.app/permissions',
  );

  @override
  void initState() {
    super.initState();
    _setupCloseAppListener();
  }

  void _setupCloseAppListener() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'closeApp') {
        print('앱 종료 신호 수신');
        // 네이티브에서 closeApp 메서드 호출
        try {
          await _channel.invokeMethod('closeApp');
        } catch (e) {
          print('앱 종료 처리 오류: $e');
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => AutoLaunchController(),
      child: MaterialApp(
        title: 'AutoLaunch',
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
          useMaterial3: true,
        ),
        home: const AppInitializerScreen(),
        debugShowCheckedModeBanner: false,
      ),
    );
  }
}

/// 앱 초기화 및 권한 체크 화면
class AppInitializerScreen extends StatefulWidget {
  const AppInitializerScreen({super.key});

  @override
  State<AppInitializerScreen> createState() => _AppInitializerScreenState();
}

class _AppInitializerScreenState extends State<AppInitializerScreen> {
  bool _isChecking = true;
  String _statusMessage = '권한 상태를 확인하는 중...';

  @override
  void initState() {
    super.initState();
    _checkPermissionsAndNavigate();
  }

  Future<void> _checkPermissionsAndNavigate() async {
    try {
      setState(() {
        _statusMessage = '앱을 초기화하는 중...';
      });

      // 잠시 대기 (스플래시 효과)
      await Future.delayed(const Duration(milliseconds: 1500));

      setState(() {
        _statusMessage = '메인 화면으로 이동 중...';
      });

      await Future.delayed(const Duration(milliseconds: 500));

      // 항상 메인 화면으로 이동 (네비게이션 선택 먼저)
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const MainScreen()),
        );
      }
    } catch (e) {
      // 오류 발생 시에도 메인 화면으로 이동
      setState(() {
        _statusMessage = '초기화 완료. 메인 화면으로 이동 중...';
      });

      await Future.delayed(const Duration(milliseconds: 500));

      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const MainScreen()),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.blue.shade400, Colors.blue.shade600],
          ),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // 앱 아이콘/로고
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.2),
                      blurRadius: 20,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.electric_car,
                  size: 60,
                  color: Colors.blue,
                ),
              ),

              const SizedBox(height: 40),

              // 앱 타이틀
              const Text(
                'AutoLaunch',
                style: TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),

              const SizedBox(height: 16),

              // 서브타이틀
              const Text(
                '충전 시 자동 네비게이션 실행',
                style: TextStyle(fontSize: 16, color: Colors.white70),
              ),

              const SizedBox(height: 60),

              // 로딩 인디케이터
              const CircularProgressIndicator(
                color: Colors.white,
                strokeWidth: 3,
              ),

              const SizedBox(height: 20),

              // 상태 메시지
              Text(
                _statusMessage,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 14, color: Colors.white70),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
