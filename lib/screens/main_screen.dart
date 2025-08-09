import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../controllers/autolaunch_controller.dart';
import '../widgets/control_buttons.dart';
import '../widgets/top_status_box.dart';
import 'settings_screen.dart';
import 'permissions_screen.dart';
import '../services/app_detection_service.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  @override
  void initState() {
    super.initState();
    // 컨트롤러 초기화
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<AutoLaunchController>().initialize();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'AutoLaunch',
          style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
        ),
        backgroundColor: Colors.blue.shade600,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.security, color: Colors.white),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const PermissionsScreen(),
                ),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.settings, color: Colors.white),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsScreen()),
              );
            },
          ),
        ],
      ),
      body: Consumer<AutoLaunchController>(
        builder: (context, controller, child) {
          if (!controller.isInitialized) {
            return const Center(child: CircularProgressIndicator());
          }

          return LayoutBuilder(
            builder: (context, constraints) {
              final isSmallScreen =
                  constraints.maxHeight < 600 || constraints.maxWidth < 400;

              return Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.blue.shade400, Colors.blue.shade600],
                  ),
                ),
                child: Column(
                  children: [
                    // 최상단 상태 박스
                    TopStatusBox(
                      isCharging: controller.isCharging,
                      isConnected: controller.isConnected,
                      isAppRunning: controller.isServiceActive,
                      targetApp: controller.targetApp,
                      isSmallScreen: isSmallScreen,
                    ),

                    // 나머지 콘텐츠
                    Expanded(
                      child: Padding(
                        padding: EdgeInsets.all(isSmallScreen ? 12.0 : 16.0),
                        child: Column(
                          children: [
                            // 중앙 상태 표시
                            Expanded(
                              flex: 3,
                              child: Center(
                                child: _buildCenterStatusWidget(
                                  controller,
                                  isSmallScreen,
                                ),
                              ),
                            ),

                            SizedBox(height: isSmallScreen ? 16 : 24),

                            // 제어 버튼들
                            ControlButtons(
                              onActivateService: () async {
                                await controller.setServiceEnabled(true);
                              },
                              onDeactivateService: () async {
                                await controller.setServiceEnabled(false);
                              },
                              hasTargetApp: controller.targetApp != null,
                              isServiceActive: controller.isServiceActive,
                              isSmallScreen: isSmallScreen,
                            ),

                            SizedBox(height: isSmallScreen ? 16 : 24),

                            // 하단 정보
                            Container(
                              padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.9),
                                borderRadius: BorderRadius.circular(
                                  isSmallScreen ? 8 : 12,
                                ),
                              ),
                              child: Text(
                                'AutoLaunch - 충전 연결 시 자동으로 네비게이션 앱을 실행합니다',
                                style: TextStyle(
                                  fontSize: isSmallScreen ? 12 : 14,
                                  color: Colors.grey.shade600,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }

  Widget _buildCenterStatusWidget(
    AutoLaunchController controller,
    bool isSmallScreen,
  ) {
    return GestureDetector(
      onTap: () => _showAppSelectionDialog(controller),
      child: Container(
        width: double.infinity,
        padding: EdgeInsets.all(isSmallScreen ? 24 : 32),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(isSmallScreen ? 16 : 24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.1),
              blurRadius: isSmallScreen ? 12 : 16,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // 상태 아이콘
            Container(
              width: isSmallScreen ? 120 : 160,
              height: isSmallScreen ? 120 : 160,
              decoration: BoxDecoration(
                color: _getCenterStatusColor(controller).withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _getCenterStatusIcon(controller),
                size: isSmallScreen ? 60 : 80,
                color: _getCenterStatusColor(controller),
              ),
            ),

            SizedBox(height: isSmallScreen ? 20 : 24),

            // 메인 텍스트
            Text(
              _getCenterMainText(controller),
              style: TextStyle(
                fontSize: isSmallScreen ? 20 : 24,
                fontWeight: FontWeight.bold,
                color: _getCenterStatusColor(controller),
              ),
              textAlign: TextAlign.center,
            ),

            SizedBox(height: isSmallScreen ? 8 : 12),

            // 서브 텍스트
            Text(
              _getCenterSubText(controller),
              style: TextStyle(
                fontSize: isSmallScreen ? 14 : 16,
                color: Colors.grey.shade600,
              ),
              textAlign: TextAlign.center,
            ),

            // 클릭 안내 (항상 표시)
            SizedBox(height: isSmallScreen ? 12 : 16),
            Container(
              padding: EdgeInsets.symmetric(
                horizontal: isSmallScreen ? 12 : 16,
                vertical: isSmallScreen ? 8 : 10,
              ),
              decoration: BoxDecoration(
                color: _getCenterStatusColor(controller).withOpacity(0.1),
                borderRadius: BorderRadius.circular(isSmallScreen ? 8 : 12),
                border: Border.all(
                  color: _getCenterStatusColor(controller).withOpacity(0.3),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.touch_app,
                    color: _getCenterStatusColor(controller),
                    size: isSmallScreen ? 16 : 18,
                  ),
                  SizedBox(width: isSmallScreen ? 6 : 8),
                  Text(
                    controller.targetApp == null ? '탭하여 앱 선택' : '탭하여 앱 변경',
                    style: TextStyle(
                      fontSize: isSmallScreen ? 12 : 14,
                      fontWeight: FontWeight.w600,
                      color: _getCenterStatusColor(controller),
                    ),
                  ),
                ],
              ),
            ),

            // 선택된 앱 정보 (있을 경우)
            if (controller.targetApp != null) ...[
              SizedBox(height: isSmallScreen ? 16 : 20),
              _buildSelectedAppInfo(controller, isSmallScreen),
            ],
          ],
        ),
      ),
    );
  }

  void _showAppSelectionDialog(AutoLaunchController controller) async {
    // 설치된 앱 목록 가져오기
    final installedApps =
        await AppDetectionService.getInstalledNavigationApps();

    if (!mounted) return;

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.6,
              maxWidth: MediaQuery.of(context).size.width * 0.9,
            ),
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // 다이얼로그 제목
                Row(
                  children: [
                    Icon(
                      Icons.navigation,
                      color: Colors.blue.shade600,
                      size: 24,
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        '네비게이션 앱 선택',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 8),

                Text(
                  installedApps.isEmpty
                      ? '설치된 네비게이션 앱이 없습니다.\n아래 앱 중 하나를 설치해주세요.'
                      : controller.targetApp == null
                      ? '설치된 네비게이션 앱 중에서 선택하세요'
                      : '다른 네비게이션 앱으로 변경하거나 선택을 해제하세요',
                  style: const TextStyle(fontSize: 14, color: Colors.grey),
                  textAlign: TextAlign.center,
                ),

                const SizedBox(height: 20),

                // 스크롤 표시 안내
                if (installedApps.length > 3 || controller.targetApp != null)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.blue.shade50,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.blue.shade200),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.swipe_vertical,
                          size: 14,
                          color: Colors.blue.shade600,
                        ),
                        const SizedBox(width: 3),
                        Text(
                          '위아래로 스크롤 가능',
                          style: TextStyle(
                            fontSize: 11,
                            color: Colors.blue.shade600,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),

                const SizedBox(height: 12),

                // 스크롤 가능한 앱 목록
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.grey.shade300),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Scrollbar(
                      thumbVisibility: true,
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            // 설치된 앱들 표시
                            if (installedApps.isNotEmpty) ...[
                              ...installedApps.map((app) {
                                final packageName = app['packageName']!;
                                final appName = app['name']!;
                                final description = app['description']!;
                                final isSelected =
                                    controller.targetApp == packageName;

                                return Container(
                                  margin: const EdgeInsets.only(bottom: 8),
                                  decoration: BoxDecoration(
                                    color:
                                        isSelected
                                            ? Colors.blue.shade50
                                            : Colors.transparent,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(
                                      color:
                                          isSelected
                                              ? Colors.blue.shade300
                                              : Colors.green.shade300,
                                      width: isSelected ? 2 : 1,
                                    ),
                                  ),
                                  child: ListTile(
                                    dense: true,
                                    contentPadding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 4,
                                    ),
                                    leading: Container(
                                      padding: const EdgeInsets.all(6),
                                      decoration: BoxDecoration(
                                        color:
                                            isSelected
                                                ? Colors.blue.shade100
                                                : Colors.green.shade100,
                                        shape: BoxShape.circle,
                                      ),
                                      child: Icon(
                                        _getAppIcon(packageName),
                                        color:
                                            isSelected
                                                ? Colors.blue.shade700
                                                : Colors.green.shade700,
                                        size: 20,
                                      ),
                                    ),
                                    title: Text(
                                      appName,
                                      style: TextStyle(
                                        fontSize: 14,
                                        fontWeight: FontWeight.w600,
                                        color:
                                            isSelected
                                                ? Colors.blue.shade700
                                                : Colors.black87,
                                      ),
                                    ),
                                    subtitle: Padding(
                                      padding: const EdgeInsets.only(top: 2),
                                      child: Text(
                                        description,
                                        style: TextStyle(
                                          fontSize: 11,
                                          color:
                                              isSelected
                                                  ? Colors.blue.shade600
                                                  : Colors.grey.shade600,
                                        ),
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
                                            : Icon(
                                              Icons.arrow_forward_ios,
                                              size: 16,
                                              color: Colors.grey.shade400,
                                            ),
                                    onTap: () {
                                      Navigator.of(context).pop();
                                      _confirmAppSelection(
                                        controller,
                                        packageName,
                                        appName,
                                      );
                                    },
                                  ),
                                );
                              }).toList(),
                            ],

                            // 미설치 앱들 표시
                            if (installedApps.isEmpty) ...[
                              ...AppDetectionService.supportedApps.entries
                                  .where(
                                    (entry) =>
                                        !installedApps.any(
                                          (app) =>
                                              app['packageName'] == entry.key,
                                        ),
                                  )
                                  .map((entry) {
                                    final packageName = entry.key;
                                    final appInfo = entry.value;
                                    final appName = appInfo['name']!;
                                    final description = appInfo['description']!;

                                    return Container(
                                      margin: const EdgeInsets.only(bottom: 8),
                                      decoration: BoxDecoration(
                                        borderRadius: BorderRadius.circular(10),
                                        border: Border.all(
                                          color: Colors.grey.shade300,
                                        ),
                                      ),
                                      child: ListTile(
                                        dense: true,
                                        contentPadding:
                                            const EdgeInsets.symmetric(
                                              horizontal: 12,
                                              vertical: 4,
                                            ),
                                        leading: Container(
                                          padding: const EdgeInsets.all(6),
                                          decoration: BoxDecoration(
                                            color: Colors.grey.shade100,
                                            shape: BoxShape.circle,
                                          ),
                                          child: Icon(
                                            _getAppIcon(packageName),
                                            color: Colors.grey.shade600,
                                            size: 20,
                                          ),
                                        ),
                                        title: Text(
                                          appName,
                                          style: const TextStyle(
                                            fontSize: 14,
                                            fontWeight: FontWeight.w600,
                                            color: Colors.black87,
                                          ),
                                        ),
                                        subtitle: Padding(
                                          padding: const EdgeInsets.only(
                                            top: 2,
                                          ),
                                          child: Text(
                                            description,
                                            style: TextStyle(
                                              fontSize: 11,
                                              color: Colors.grey.shade600,
                                            ),
                                          ),
                                        ),
                                        trailing: Icon(
                                          Icons.download,
                                          size: 16,
                                          color: Colors.grey.shade400,
                                        ),
                                        onTap: () {
                                          // 미설치 앱 선택 시 안내 메시지
                                          ScaffoldMessenger.of(
                                            context,
                                          ).showSnackBar(
                                            SnackBar(
                                              content: Text(
                                                '$appName 앱을 먼저 설치해주세요.',
                                              ),
                                              backgroundColor: Colors.orange,
                                            ),
                                          );
                                        },
                                      ),
                                    );
                                  })
                                  .toList(),
                            ],

                            // 선택 해제 옵션 (앱이 선택되어 있을 때만)
                            if (controller.targetApp != null) ...[
                              const SizedBox(height: 8),
                              Container(
                                margin: const EdgeInsets.only(bottom: 8),
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(
                                    color: Colors.grey.shade300,
                                  ),
                                ),
                                child: ListTile(
                                  dense: true,
                                  contentPadding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 4,
                                  ),
                                  leading: Container(
                                    padding: const EdgeInsets.all(6),
                                    decoration: BoxDecoration(
                                      color: Colors.grey.shade100,
                                      shape: BoxShape.circle,
                                    ),
                                    child: Icon(
                                      Icons.close,
                                      color: Colors.grey.shade600,
                                      size: 20,
                                    ),
                                  ),
                                  title: const Text(
                                    '선택 해제',
                                    style: TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                      color: Colors.black87,
                                    ),
                                  ),
                                  subtitle: const Text(
                                    '자동 실행 기능을 사용하지 않습니다',
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: Colors.grey,
                                    ),
                                  ),
                                  trailing: Icon(
                                    Icons.arrow_forward_ios,
                                    size: 16,
                                    color: Colors.grey.shade400,
                                  ),
                                  onTap: () {
                                    controller.setTargetApp(null);
                                    Navigator.of(context).pop();
                                  },
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                ),

                const SizedBox(height: 16),

                // 취소 버튼
                SizedBox(
                  width: double.infinity,
                  child: TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    child: const Text(
                      '취소',
                      style: TextStyle(fontSize: 16, color: Colors.grey),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _confirmAppSelection(
    AutoLaunchController controller,
    String packageName,
    String appName,
  ) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('앱 선택 확인'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('$appName 앱을 자동 실행 대상으로 설정하시겠습니까?'),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.blue.shade200),
                ),
                child: Row(
                  children: [
                    Icon(Icons.info, color: Colors.blue.shade600, size: 18),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        '설정 후 권한 화면으로 이동합니다',
                        style: TextStyle(fontSize: 13, color: Colors.black87),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('취소'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('확인'),
            ),
          ],
        );
      },
    );

    if (confirmed == true) {
      // 앱 설정
      controller.setTargetApp(packageName);

      // 성공 메시지
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('✅ $appName 앱이 설정되었습니다! 권한 설정을 진행하세요.'),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 2),
        ),
      );

      // 1초 후 권한 설정 화면으로 자동 이동
      await Future.delayed(const Duration(seconds: 1));

      if (mounted) {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const PermissionsScreen()),
        );
      }
    }
  }

  Widget _buildSelectedAppInfo(
    AutoLaunchController controller,
    bool isSmallScreen,
  ) {
    return Container(
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      decoration: BoxDecoration(
        color: Colors.blue.shade50,
        borderRadius: BorderRadius.circular(isSmallScreen ? 8 : 12),
        border: Border.all(color: Colors.blue.shade200),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            _getAppIcon(controller.targetApp!),
            color: Colors.blue.shade700,
            size: isSmallScreen ? 20 : 24,
          ),
          SizedBox(width: isSmallScreen ? 8 : 12),
          Text(
            '선택된 앱: ${_getAppName(controller.targetApp!)}',
            style: TextStyle(
              fontSize: isSmallScreen ? 14 : 16,
              fontWeight: FontWeight.w600,
              color: Colors.blue.shade700,
            ),
          ),
        ],
      ),
    );
  }

  Color _getCenterStatusColor(AutoLaunchController controller) {
    if (controller.targetApp == null) {
      return Colors.orange.shade600;
    }
    if (controller.isServiceActive) {
      return Colors.green.shade600;
    }
    return Colors.blue.shade600;
  }

  IconData _getCenterStatusIcon(AutoLaunchController controller) {
    if (controller.targetApp == null) {
      return Icons.settings;
    }
    if (controller.isServiceActive) {
      return Icons.play_circle_filled;
    }
    return Icons.pause_circle_filled;
  }

  String _getCenterMainText(AutoLaunchController controller) {
    if (controller.targetApp == null) {
      return '네비게이션 앱 설정';
    }
    if (controller.isServiceActive) {
      return '앱 실행 중';
    }
    return '앱 대기 중';
  }

  String _getCenterSubText(AutoLaunchController controller) {
    if (controller.targetApp == null) {
      return '기본으로 네비 어플을 설정해주세요\n\n4가지 네비게이션 중 선택:\nT맵, 카카오네비, 카카오맵, 네이버맵';
    }
    if (controller.isServiceActive) {
      return '${_getAppName(controller.targetApp!)}이 실행 중입니다';
    }
    return '${_getAppName(controller.targetApp!)}이 대기 중입니다';
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
      case 'com.google.android.apps.maps':
        return Icons.public;
      case 'com.waze':
        return Icons.traffic;
      default:
        return Icons.apps;
    }
  }

  String _getAppName(String packageName) {
    switch (packageName) {
      case 'com.skt.tmap.ku':
        return 'T맵';
      case 'com.kakao.navi':
        return '카카오네비';
      case 'net.daum.android.map':
        return '카카오맵';
      case 'com.nhn.android.nmap':
        return '네이버지도';
      case 'com.google.android.apps.maps':
        return '구글맵';
      case 'com.waze':
        return 'Waze';
      default:
        return '네비게이션 앱';
    }
  }
}
