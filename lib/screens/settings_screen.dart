import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../controllers/autolaunch_controller.dart';
import '../services/settings_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const platform = MethodChannel('autolaunch/control');

  String _logContent = '';
  bool _isLoadingLogs = false;
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('설정'),
        backgroundColor: Colors.blue.shade600,
        foregroundColor: Colors.white,
      ),
      body: Consumer<AutoLaunchController>(
        builder: (context, controller, child) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // 대상 앱 선택
              _buildTargetAppSelection(controller),

              const SizedBox(height: 24),

              // 지연 시간 설정
              _buildDelayTimeSetting(controller),

              const SizedBox(height: 24),

              // 고급 설정
              _buildAdvancedSettings(controller),

              const SizedBox(height: 24),

              // 🔥 디버그 로그 뷰어
              _buildLogViewer(),
            ],
          );
        },
      ),
    );
  }

  Widget _buildTargetAppSelection(AutoLaunchController controller) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '자동 실행할 네비게이션 앱',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              '충전 연결 시 자동으로 실행할 네비게이션 앱을 선택하세요',
              style: TextStyle(color: Colors.grey, fontSize: 14),
            ),
            const SizedBox(height: 16),

            // 3가지 네비게이션 앱 선택
            ...SettingsService.commonAppNames.asMap().entries.map((entry) {
              final index = entry.key;
              final appName = entry.value;
              final packageName = SettingsService.commonNavigationApps[index];
              final isSelected = controller.targetApp == packageName;

              return AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                margin: const EdgeInsets.only(bottom: 8),
                decoration: BoxDecoration(
                  color: isSelected ? Colors.blue.shade50 : Colors.transparent,
                  borderRadius: BorderRadius.circular(8),
                  border:
                      isSelected
                          ? Border.all(color: Colors.blue.shade300, width: 2)
                          : Border.all(color: Colors.grey.shade300, width: 1),
                ),
                child: ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color:
                          isSelected
                              ? Colors.blue.shade100
                              : Colors.grey.shade100,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      _getAppIcon(packageName),
                      color:
                          isSelected
                              ? Colors.blue.shade700
                              : Colors.grey.shade600,
                      size: 24,
                    ),
                  ),
                  title: Text(
                    appName,
                    style: TextStyle(
                      fontWeight:
                          isSelected ? FontWeight.bold : FontWeight.normal,
                      color: isSelected ? Colors.blue.shade700 : Colors.black87,
                    ),
                  ),
                  subtitle: Text(
                    _getAppDescription(packageName),
                    style: TextStyle(
                      color:
                          isSelected
                              ? Colors.blue.shade600
                              : Colors.grey.shade600,
                      fontSize: 12,
                    ),
                  ),
                  trailing:
                      isSelected
                          ? Container(
                            padding: const EdgeInsets.all(4),
                            decoration: BoxDecoration(
                              color: Colors.blue.shade600,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.check,
                              color: Colors.white,
                              size: 16,
                            ),
                          )
                          : null,
                  onTap: () {
                    controller.setTargetApp(packageName);
                  },
                ),
              );
            }),

            const SizedBox(height: 16),

            // 선택 해제 옵션
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              decoration: BoxDecoration(
                color:
                    controller.targetApp == null
                        ? Colors.red.shade50
                        : Colors.transparent,
                borderRadius: BorderRadius.circular(8),
                border:
                    controller.targetApp == null
                        ? Border.all(color: Colors.red.shade300, width: 2)
                        : Border.all(color: Colors.grey.shade300, width: 1),
              ),
              child: ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color:
                        controller.targetApp == null
                            ? Colors.red.shade100
                            : Colors.grey.shade100,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    Icons.close,
                    color:
                        controller.targetApp == null
                            ? Colors.red.shade700
                            : Colors.grey.shade600,
                    size: 24,
                  ),
                ),
                title: Text(
                  '선택 안함',
                  style: TextStyle(
                    fontWeight:
                        controller.targetApp == null
                            ? FontWeight.bold
                            : FontWeight.normal,
                    color:
                        controller.targetApp == null
                            ? Colors.red.shade700
                            : Colors.black87,
                  ),
                ),
                subtitle: Text(
                  '자동 실행 기능을 사용하지 않습니다',
                  style: TextStyle(
                    color:
                        controller.targetApp == null
                            ? Colors.red.shade600
                            : Colors.grey.shade600,
                    fontSize: 12,
                  ),
                ),
                trailing:
                    controller.targetApp == null
                        ? Container(
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: Colors.red.shade600,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.check,
                            color: Colors.white,
                            size: 16,
                          ),
                        )
                        : null,
                onTap: () {
                  controller.setTargetApp(null);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  IconData _getAppIcon(String packageName) {
    switch (packageName) {
      case 'com.skt.tmap.ku':
        return Icons.directions_car;
      case 'com.kakao.navi':
        return Icons.navigation;
      case 'net.daum.android.map':
        return Icons.map;
      case 'com.nhn.android.nmap':
        return Icons.location_on;
      default:
        return Icons.apps;
    }
  }

  String _getAppDescription(String packageName) {
    switch (packageName) {
      case 'com.skt.tmap.ku':
        return 'SK텔레콤 네비게이션';
      case 'com.kakao.navi':
        return '카카오 전용 네비게이션';
      case 'net.daum.android.map':
        return '카카오 지도 및 길찾기';
      case 'com.nhn.android.nmap':
        return '네이버 지도';
      default:
        return '';
    }
  }

  Widget _buildDelayTimeSetting(AutoLaunchController controller) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '연결 해제 지연 시간',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              '충전 해제 후 앱을 종료하기까지 대기하는 시간입니다',
              style: TextStyle(color: Colors.grey, fontSize: 14),
            ),
            const SizedBox(height: 16),

            Slider(
              value: controller.delayTime.toDouble(),
              min: 1,
              max: 10,
              divisions: 9,
              label: '${controller.delayTime}초',
              onChanged: (value) {
                controller.setDelayTime(value.toInt());
              },
            ),

            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: const [Text('1초'), Text('10초')],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAdvancedSettings(AutoLaunchController controller) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '고급 설정',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),

            // 블루투스 예외
            SwitchListTile(
              title: const Text('블루투스 예외'),
              subtitle: const Text('특정 블루투스 연결 시에도 실행'),
              value: false, // TODO: 구현 필요
              onChanged: (value) {
                controller.setBluetoothException(value);
              },
              secondary: const Icon(Icons.bluetooth),
            ),

            // 배터리 최적화 무시
            SwitchListTile(
              title: const Text('배터리 최적화 무시'),
              subtitle: const Text('배터리 최적화 정책을 무시하고 실행'),
              value: false, // TODO: 구현 필요
              onChanged: (value) {
                controller.setBatteryOptimization(value);
              },
              secondary: const Icon(Icons.battery_charging_full),
            ),
          ],
        ),
      ),
    );
  }

  // 🔥 로그 뷰어 카드
  Widget _buildLogViewer() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.bug_report, color: Colors.orange.shade600),
                const SizedBox(width: 8),
                Text(
                  '🔥 디버그 로그 뷰어',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Colors.orange.shade600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              '충전 해제 시 로그를 확인할 수 있습니다.',
              style: TextStyle(color: Colors.grey.shade600),
            ),
            const SizedBox(height: 16),

            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isLoadingLogs ? null : _loadLogs,
                    icon:
                        _isLoadingLogs
                            ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                            : const Icon(Icons.refresh),
                    label: Text(_isLoadingLogs ? '로드 중...' : '로그 새로고침'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange.shade600,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton.icon(
                  onPressed: _clearLogs,
                  icon: const Icon(Icons.clear_all),
                  label: const Text('로그 삭제'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red.shade600,
                    foregroundColor: Colors.white,
                  ),
                ),
              ],
            ),

            if (_logContent.isNotEmpty) ...[
              const SizedBox(height: 16),
              Container(
                height: 300,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey.shade300),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '로그 내용 (최근부터):',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Colors.grey.shade700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Expanded(
                      child: SingleChildScrollView(
                        reverse: true, // 최신 로그가 아래에 오도록
                        child: SelectableText(
                          _logContent,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 12,
                            color: Colors.black87,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  // 로그 파일 로드
  Future<void> _loadLogs() async {
    setState(() {
      _isLoadingLogs = true;
    });

    try {
      final result = await platform.invokeMethod('getLogFile');
      setState(() {
        _logContent = result ?? '로그가 없습니다.';
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('🔥 로그를 성공적으로 로드했습니다!'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _logContent = '로그 로드 오류: $e';
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('로그 로드 실패: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      setState(() {
        _isLoadingLogs = false;
      });
    }
  }

  // 로그 파일 삭제
  Future<void> _clearLogs() async {
    try {
      await platform.invokeMethod('clearLogs');
      setState(() {
        _logContent = '';
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('🔥 로그가 삭제되었습니다!'),
            backgroundColor: Colors.orange,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('로그 삭제 실패: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }
}
