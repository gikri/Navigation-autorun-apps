import 'package:flutter/material.dart';

class StatusCard extends StatelessWidget {
  final bool isConnected;
  final bool isCharging;
  final bool isServiceActive;
  final String? targetApp;
  final bool isSmallScreen;

  const StatusCard({
    super.key,
    required this.isConnected,
    required this.isCharging,
    required this.isServiceActive,
    this.targetApp,
    this.isSmallScreen = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.all(isSmallScreen ? 16 : 20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(isSmallScreen ? 12 : 16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: isSmallScreen ? 8 : 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        children: [
          // 상태 아이콘
          Container(
            width: isSmallScreen ? 60 : 80,
            height: isSmallScreen ? 60 : 80,
            decoration: BoxDecoration(
              color: _getStatusColor().withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(
              _getStatusIcon(),
              size: isSmallScreen ? 30 : 40,
              color: _getStatusColor(),
            ),
          ),

          SizedBox(height: isSmallScreen ? 12 : 16),

          // 상태 텍스트
          Text(
            _getStatusText(),
            style: TextStyle(
              fontSize: isSmallScreen ? 16 : 18,
              fontWeight: FontWeight.bold,
              color: _getStatusColor(),
            ),
          ),

          SizedBox(height: isSmallScreen ? 6 : 8),

          // 서브 텍스트
          Text(
            _getSubText(),
            style: TextStyle(
              fontSize: isSmallScreen ? 12 : 14,
              color: Colors.grey,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Color _getStatusColor() {
    if (targetApp == null) {
      return Colors.grey;
    }
    if (isServiceActive) {
      return Colors.green;
    }
    return Colors.orange;
  }

  IconData _getStatusIcon() {
    if (targetApp == null) {
      return Icons.apps;
    }
    if (isServiceActive) {
      return Icons.play_circle_filled;
    }
    return Icons.pause_circle_filled;
  }

  String _getStatusText() {
    if (targetApp == null) {
      return '앱 미선택';
    }
    if (isServiceActive) {
      return '앱 실행 중';
    }
    return '앱 대기 중';
  }

  String _getSubText() {
    if (targetApp == null) {
      return '설정에서 네비게이션 앱을 선택하세요';
    }
    if (isServiceActive) {
      return '${_getAppName(targetApp!)}이 실행 중입니다';
    }
    return '${_getAppName(targetApp!)}이 대기 중입니다';
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
        return '네이버맵';
      default:
        return '네비게이션 앱';
    }
  }
}
