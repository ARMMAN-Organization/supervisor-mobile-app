package org.armman.supervisor.data.connectivity

class FakeConnectivityChecker(var online: Boolean = true) : ConnectivityChecker {
  override fun isOnline(): Boolean = online
}
