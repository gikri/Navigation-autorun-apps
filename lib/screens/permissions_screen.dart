import 'package:flutter/material.dart';
import '../services/permission_service.dart';

class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen>
    with WidgetsBindingObserver {
  Map<String, bool> permissions = {};
  Map<String, dynamic> deviceInfo = {};
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadPermissions();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // 앱이 다시 활성화될 때 권한 상태 새로고침
    if (state == AppLifecycleState.resumed) {
      _loadPermissions();
    }
  }

  Future<void> _loadPermissions() async {
    setState(() {
      isLoading = true;
    });

    try {
      final result = await PermissionService.checkPermissions();
      final deviceResult = await PermissionService.getDeviceVendorInfo();

      // 🔥 샤오미 자동실행 권한 상태 추가 로드
      if (deviceResult['isXiaomi'] == true) {
        final extendedCheck =
            await PermissionService.getExtendedBackgroundReadiness();
        final vendorPermissions =
            extendedCheck['vendorPermissions'] as Map<String, bool>? ?? {};

        // permissions 맵에 xiaomi_autostart 상태 추가
        result['xiaomi_autostart'] =
            vendorPermissions['xiaomi_autostart'] ?? false;
      }

      setState(() {
        permissions = result;
        deviceInfo = deviceResult;
        isLoading = false;
      });
    } catch (e) {
      print('권한 로드 오류: $e');
      setState(() {
        isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('권한 상태 확인 중 오류가 발생했습니다: $e'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '권한 설정',
          style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
        ),
        backgroundColor: Colors.blue.shade600,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.blue.shade50, Colors.white],
          ),
        ),
        child:
            isLoading
                ? const Center(child: CircularProgressIndicator())
                : _buildPermissionsList(),
      ),
    );
  }

  Widget _buildPermissionsList() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.blue.shade50,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.blue.shade200),
            ),
            child: Column(
              children: [
                Icon(Icons.info, color: Colors.blue.shade600, size: 28),
                const SizedBox(height: 8),
                Text(
                  '잠금 상태에서도 정상 작동하려면\n다음 4가지 핵심 권한이 필요합니다',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Colors.blue.shade800,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 핵심 권한들 (레퍼런스 앱 기준)
          _buildSectionTitle('🔑 필수 핵심 권한 (4가지)'),
          const SizedBox(height: 12),

          // 1. 시스템 설정 수정 허용
          _buildPermissionCard(
            'write_settings',
            '1. 시스템 설정 수정 허용',
            '화면 밝기 및 시스템 설정을 제어하기 위해 반드시 필요합니다.',
            Icons.settings,
            Colors.blue,
            () => _requestWriteSettingsPermission(),
            priority: 1,
          ),

          const SizedBox(height: 12),

          // 2. 배터리 제한 해제
          _buildPermissionCard(
            'battery_optimization',
            '2. 배터리 제한 해제',
            '잠금 상태에서도 백그라운드 실행을 유지하기 위해 반드시 필요합니다.',
            Icons.battery_charging_full,
            Colors.green,
            () => _requestBatteryOptimization(),
            priority: 1,
          ),

          const SizedBox(height: 12),

          // 3. 다른앱 위에 그리기 권한
          _buildPermissionCard(
            'system_alert_window',
            '3. 다른앱 위에 그리기 권한',
            '잠금 화면에서 앱을 실행하고 화면을 깨우기 위해 반드시 필요합니다.',
            Icons.layers,
            Colors.orange,
            () => _requestSystemAlertWindowPermission(),
            priority: 1,
          ),

          const SizedBox(height: 12),

          // 4. 접근성 권한
          _buildPermissionCard(
            'accessibility_service',
            '4. 접근성 권한',
            '화면 끄기 및 고급 제어 기능을 위해 반드시 필요합니다.',
            Icons.accessibility_new,
            Colors.purple,
            () => _requestAccessibilityServicePermission(),
            priority: 1,
          ),

          const SizedBox(height: 24),

          // 샤오미 전용 권한 섹션 (조건부 표시)
          if (deviceInfo['isXiaomi'] == true) ...[
            _buildSectionTitle('🔥 ${deviceInfo['vendorName']} 전용 권한'),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.red.shade50,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.red.shade200),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.warning_amber,
                    color: Colors.red.shade600,
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '샤오미/레드미 기기에서는 추가 권한이 필요합니다',
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.red.shade700,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // 5. 샤오미 자동 실행 권한
            _buildXiaomiAutostartCard(),

            const SizedBox(height: 24),
          ],

          // 보조 권한들
          _buildSectionTitle('⚙️ 보조 권한 (선택사항)'),
          const SizedBox(height: 12),

          // 디바이스 관리자 권한 (대안)
          _buildPermissionCard(
            'device_admin',
            '디바이스 관리자 권한',
            '접근성 권한 대신 사용할 수 있는 대안 방법입니다.',
            Icons.admin_panel_settings,
            Colors.grey,
            () => _requestDeviceAdminPermission(),
            priority: 2,
          ),

          const SizedBox(height: 30),

          // 백그라운드 실행 준비 체크 버튼 (새로 추가)
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _checkBackgroundReadiness,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green.shade600,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                '백그라운드 실행 준비 체크',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
          ),

          const SizedBox(height: 16),

          // 핵심 4가지 권한 일괄 요청 버튼
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _requestCorePermissions,
              icon: const Icon(Icons.security, size: 20),
              label: Text(
                deviceInfo['isXiaomi'] == true
                    ? '핵심 5가지 권한 일괄 요청 (샤오미 전용 포함)'
                    : '핵심 4가지 권한 일괄 요청',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red.shade600,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 4,
              ),
            ),
          ),

          const SizedBox(height: 12),

          // 전체 권한 요청 버튼
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _requestAllPermissions,
              icon: const Icon(Icons.apps, size: 20),
              label: const Text(
                '모든 권한 요청',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.blue.shade600,
                side: BorderSide(color: Colors.blue.shade600, width: 2),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),

          const SizedBox(height: 16),

          // 권한 상태 새로고침 버튼
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: _loadPermissions,
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.blue.shade600,
                side: BorderSide(color: Colors.blue.shade600),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                '권한 상태 새로고침',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
          ),

          const SizedBox(height: 20),

          // 권한 설명
          _buildInfoCard(),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.bold,
        color: Colors.grey.shade800,
      ),
    );
  }

  Widget _buildPermissionCard(
    String key,
    String title,
    String description,
    IconData icon,
    Color color,
    VoidCallback onTap, {
    int priority = 0,
  }) {
    final isGranted = permissions[key] ?? false;

    return Card(
      elevation: priority == 1 ? 4 : 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side:
            priority == 1
                ? BorderSide(
                  color: isGranted ? Colors.green : Colors.red,
                  width: 2,
                )
                : BorderSide.none,
      ),
      child: InkWell(
        onTap: isGranted ? null : onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          decoration:
              priority == 1
                  ? BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    gradient: LinearGradient(
                      colors: [
                        color.withOpacity(0.03),
                        color.withOpacity(0.06),
                      ],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  )
                  : null,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border:
                        priority == 1
                            ? Border.all(
                              color: color.withOpacity(0.3),
                              width: 1,
                            )
                            : null,
                  ),
                  child: Icon(icon, color: color, size: 24),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        description,
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color:
                        isGranted
                            ? Colors.green.withOpacity(0.1)
                            : Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        isGranted ? Icons.check_circle : Icons.cancel,
                        size: 16,
                        color: isGranted ? Colors.green : Colors.red,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        isGranted ? '허용됨' : '거부됨',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: isGranted ? Colors.green : Colors.red,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoCard() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.info_outline, color: Colors.blue.shade600, size: 24),
                const SizedBox(width: 12),
                Text(
                  '권한 안내',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Colors.blue.shade600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            RichText(
              text: TextSpan(
                style: const TextStyle(
                  fontSize: 14,
                  color: Colors.grey,
                  height: 1.6,
                ),
                children: [
                  const TextSpan(text: '🔑 ', style: TextStyle(fontSize: 16)),
                  const TextSpan(
                    text: '핵심 4가지 권한',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      color: Colors.black87,
                    ),
                  ),
                  const TextSpan(text: ': 잠금 상태에서도 정상 작동하기 위해 반드시 필요합니다.\n\n'),

                  const TextSpan(
                    text: '📱 시스템 설정 수정',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Colors.blue,
                    ),
                  ),
                  const TextSpan(text: ': 화면 밝기 및 시스템 제어\n'),

                  const TextSpan(
                    text: '🔋 배터리 제한 해제',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Colors.green,
                    ),
                  ),
                  const TextSpan(text: ': 백그라운드 지속 실행 보장\n'),

                  const TextSpan(
                    text: '🔝 다른앱 위에 그리기',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Colors.orange,
                    ),
                  ),
                  const TextSpan(text: ': 잠금 화면에서 앱 실행\n'),

                  const TextSpan(
                    text: '♿ 접근성 권한',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Colors.purple,
                    ),
                  ),
                  const TextSpan(text: ': 화면 끄기 및 고급 제어\n'),

                  // 샤오미 기기일 때만 추가 설명 표시
                  if (deviceInfo['isXiaomi'] == true) ...[
                    const TextSpan(text: '\n'),
                    const TextSpan(
                      text: '🔥 MIUI 자동 실행',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        color: Colors.red,
                      ),
                    ),
                    const TextSpan(text: ': 샤오미/레드미 기기 전용 권한\n'),
                  ],

                  const TextSpan(text: '\n⚙️ ', style: TextStyle(fontSize: 16)),
                  const TextSpan(
                    text: '보조 권한',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      color: Colors.black87,
                    ),
                  ),
                  const TextSpan(text: ': 필요에 따라 선택적으로 설정할 수 있습니다.'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildXiaomiAutostartCard() {
    final isGranted = permissions['xiaomi_autostart'] ?? false;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.red.shade200, width: 2),
        boxShadow: [
          BoxShadow(
            color: Colors.red.withOpacity(0.1),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.red.shade50,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.rocket_launch, color: Colors.red, size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '5. MIUI 자동 실행 권한',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey.shade800,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '잠금 상태와 재부팅 후에도 앱이 자동으로 실행되도록 보장합니다.',
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isGranted ? Colors.green : Colors.red.shade300,
                  ),
                  child: Icon(
                    isGranted ? Icons.check : Icons.close,
                    size: 14,
                    color: Colors.white,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 12),

            // 설정 안내
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.orange.shade50,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.orange.shade200),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '🔧 수동 설정 필요',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Colors.orange.shade800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '설정 → 앱 관리 → AutoLaunch → 자동 실행 → 허용',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.orange.shade700,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 12),

            // 버튼들
            Row(
              children: [
                Expanded(
                  flex: 2,
                  child: ElevatedButton.icon(
                    onPressed: () => _requestXiaomiAutostartPermission(),
                    icon: const Icon(Icons.settings, size: 16),
                    label: const Text('설정 열기', style: TextStyle(fontSize: 12)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red.shade600,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ),

                const SizedBox(width: 8),

                Expanded(
                  flex: 2,
                  child: OutlinedButton.icon(
                    onPressed:
                        isGranted
                            ? null
                            : () => _markXiaomiAutostartCompleted(),
                    icon: Icon(isGranted ? Icons.check : Icons.done, size: 16),
                    label: Text(
                      isGranted ? '설정 완료됨' : '설정 완료',
                      style: const TextStyle(fontSize: 12),
                    ),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: isGranted ? Colors.green : Colors.blue,
                      side: BorderSide(
                        color: isGranted ? Colors.green : Colors.blue,
                        width: 1.5,
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _requestBatteryOptimization() async {
    await PermissionService.requestBatteryOptimization();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissions();
  }

  Future<void> _requestSystemAlertWindowPermission() async {
    await PermissionService.requestSystemAlertWindow();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissions();
  }

  Future<void> _requestWriteSettingsPermission() async {
    await PermissionService.requestWriteSettings();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissions();
  }

  Future<void> _requestAccessibilityServicePermission() async {
    await PermissionService.requestAccessibilityService();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissions();
  }

  Future<void> _requestDeviceAdminPermission() async {
    await PermissionService.requestDeviceAdmin();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissions();
  }

  Future<void> _requestXiaomiAutostartPermission() async {
    await PermissionService.requestXiaomiAutostartPermission();
    // 샤오미 자동 실행 권한은 확인이 어려우므로 사용자에게 설정 완료 후 새로고침 안내
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('MIUI 자동 실행 설정에서 이 앱을 허용한 후 "새로고침" 버튼을 눌러주세요.'),
        backgroundColor: Colors.orange,
        duration: Duration(seconds: 4),
      ),
    );
  }

  Future<void> _requestCorePermissions() async {
    try {
      setState(() {
        isLoading = true;
      });

      final isXiaomi = deviceInfo['isXiaomi'] == true;
      final totalPermissions = isXiaomi ? 5 : 4;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            isXiaomi
                ? '핵심 5가지 권한을 순차적으로 요청합니다... (샤오미 전용 포함)'
                : '핵심 4가지 권한을 순차적으로 요청합니다...',
          ),
          backgroundColor: Colors.blue,
          duration: const Duration(seconds: 2),
        ),
      );

      // 1. 시스템 설정 수정 허용
      await _requestWriteSettingsPermission();
      await Future.delayed(const Duration(seconds: 2));

      // 2. 배터리 제한 해제
      await _requestBatteryOptimization();
      await Future.delayed(const Duration(seconds: 2));

      // 3. 다른앱 위에 그리기 권한
      await _requestSystemAlertWindowPermission();
      await Future.delayed(const Duration(seconds: 2));

      // 4. 접근성 권한
      await _requestAccessibilityServicePermission();
      await Future.delayed(const Duration(seconds: 2));

      // 5. 샤오미 자동 실행 권한 (샤오미 기기인 경우에만)
      if (isXiaomi) {
        await _requestXiaomiAutostartPermission();
        await Future.delayed(const Duration(seconds: 2));
      }

      setState(() {
        isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            isXiaomi
                ? '핵심 5가지 권한 요청이 완료되었습니다. 설정을 확인해주세요.'
                : '핵심 4가지 권한 요청이 완료되었습니다. 설정을 확인해주세요.',
          ),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 3),
        ),
      );

      // 권한 상태 새로고침
      await _loadPermissions();
    } catch (e) {
      setState(() {
        isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('권한 요청 중 오류가 발생했습니다: $e'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  Future<void> _requestAllPermissions() async {
    await PermissionService.requestAllPermissions();
    // 잠시 대기 후 권한 상태 새로고침
    await Future.delayed(const Duration(seconds: 1));
    await _loadPermissions();
  }

  Future<void> _checkBackgroundReadiness() async {
    try {
      // 로딩 상태 표시
      setState(() {
        isLoading = true;
      });

      // 확장된 백그라운드 실행 준비 상태 체크 (제조사별 권한 포함)
      final extendedCheck =
          await PermissionService.getExtendedBackgroundReadiness();

      setState(() {
        isLoading = false;
      });

      // 결과에 따라 사용자에게 알림
      final allReady = extendedCheck['allPermissionsReady'] ?? false;
      final coreReady = extendedCheck['corePermissionsReady'] ?? false;
      final needsVendor = extendedCheck['needsVendorPermissions'] ?? false;
      final message = extendedCheck['message'] ?? '권한 설정이 필요합니다.';
      final totalGranted = extendedCheck['totalGrantedCount'] ?? 0;
      final totalRequired = extendedCheck['totalRequiredCount'] ?? 4;

      if (allReady) {
        // 모든 권한이 준비됨
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('✅ $message'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
      } else {
        // 일부 권한이 부족함

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  needsVendor
                      ? '⚙️ 제조사별 추가 권한 설정이 필요합니다'
                      : '⚠️ 백그라운드 실행 설정이 필요합니다',
                ),
                const SizedBox(height: 4),
                Text(message, style: const TextStyle(fontSize: 12)),
                const SizedBox(height: 4),
                Text(
                  '진행률: $totalGranted/$totalRequired 완료',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            backgroundColor: needsVendor ? Colors.red : Colors.orange,
            duration: const Duration(seconds: 5),
            action: SnackBarAction(
              label: '설정',
              textColor: Colors.white,
              onPressed: () => _requestAllPermissions(),
            ),
          ),
        );
      }

      // 권한 상태 새로고침
      await Future.delayed(const Duration(milliseconds: 500));
      await _loadPermissions();
    } catch (e) {
      setState(() {
        isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('권한 체크 중 오류가 발생했습니다: $e'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  Future<void> _markXiaomiAutostartCompleted() async {
    try {
      print('🔥 MIUI 자동실행 권한 설정 완료 처리 시작');

      // 로딩 상태 표시
      setState(() {
        isLoading = true;
      });

      // 샤오미 자동실행 권한을 설정 완료로 표시
      await PermissionService.markXiaomiAutostartAsCompleted();
      print('🔥 MIUI 자동실행 권한 로컬 저장 완료');

      // 권한 상태 새로고침
      await _loadPermissions();
      print('🔥 권한 상태 새로고침 완료');

      setState(() {
        isLoading = false;
      });

      // 성공 메시지 및 다음 단계 안내
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('✅ MIUI 자동 실행 권한이 설정 완료로 표시되었습니다!'),
              const SizedBox(height: 4),
              const Text(
                '이제 "백그라운드 실행 준비 체크" 버튼으로 전체 상태를 확인해보세요.',
                style: TextStyle(fontSize: 12),
              ),
            ],
          ),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 4),
          action: SnackBarAction(
            label: '체크',
            textColor: Colors.white,
            onPressed: () => _checkBackgroundReadiness(),
          ),
        ),
      );
    } catch (e) {
      print('🔥 MIUI 자동실행 권한 설정 완료 처리 오류: $e');

      setState(() {
        isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('❌ 설정 완료 처리 중 오류가 발생했습니다'),
              const SizedBox(height: 4),
              Text('오류 내용: $e', style: const TextStyle(fontSize: 11)),
              const SizedBox(height: 4),
              const Text(
                '앱을 다시 시작해보거나 개발자에게 문의해주세요.',
                style: TextStyle(fontSize: 11),
              ),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 5),
        ),
      );
    }
  }
}
