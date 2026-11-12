package com.univote.evoting.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RECOVER = "recover"
    const val HOME = "home"

    const val ADMIN_DEPARTMENTS = "admin/departments"
    const val ADMIN_VOTERS = "admin/voters"
    const val ADMIN_PARTIES = "admin/parties"
    const val ADMIN_ELECTIONS = "admin/elections"
    const val ELECTION_DETAIL = "admin/election/{electionId}"

    fun electionDetail(id: Long) = "admin/election/$id"
    const val SUPPORT = "support"
}