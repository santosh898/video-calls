package com.videocalls.app.ui.navigation

sealed class Screen(val route: String) {
    object SimIdentity : Screen("sim_identity")
    object Login : Screen("login")
    object Home : Screen("home")
    object CreateGroup : Screen("create_group")
    object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/$groupId"
    }
    object SingleCall : Screen("single_call/{callId}?isOutgoing={isOutgoing}&targetName={targetName}&targetPhone={targetPhone}&isVideo={isVideo}&isSoloTest={isSoloTest}") {
        fun createRoute(callId: String, isOutgoing: Boolean, targetName: String, targetPhone: String, isVideo: Boolean, isSoloTest: Boolean = false): String {
            return "single_call/$callId?isOutgoing=$isOutgoing&targetName=$targetName&targetPhone=$targetPhone&isVideo=$isVideo&isSoloTest=$isSoloTest"
        }
    }
    object GroupCall : Screen("group_call/{callId}?groupId={groupId}&groupName={groupName}&isVideo={isVideo}") {
        fun createRoute(callId: String, groupId: String, groupName: String, isVideo: Boolean): String {
            return "group_call/$callId?groupId=$groupId&groupName=$groupName&isVideo=$isVideo"
        }
    }
    object IncomingCall : Screen("incoming_call/{callId}?callerName={callerName}&callerPhone={callerPhone}&callType={callType}&isGroupCall={isGroupCall}&groupId={groupId}") {
        fun createRoute(callId: String, callerName: String, callerPhone: String, callType: String, isGroupCall: Boolean, groupId: String?): String {
            return "incoming_call/$callId?callerName=$callerName&callerPhone=$callerPhone&callType=$callType&isGroupCall=$isGroupCall&groupId=${groupId ?: ""}"
        }
    }
}
