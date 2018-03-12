package cz.skala.trezorwallet.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import cz.skala.trezorwallet.data.AppDatabase
import cz.skala.trezorwallet.data.entity.Account

/**
 * A ViewModel for MainActivity.
 */
class MainViewModel(database: AppDatabase) : ViewModel() {
    val accounts: LiveData<List<Account>> by lazy {
        database.accountDao().getAllLiveData()
    }

    val selectedAccount = MutableLiveData<Account>()

    class Factory(val database: AppDatabase) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MainViewModel(database) as T
        }
    }

    fun setSelectedAccount(account: Account) {
        if (selectedAccount.value != account) {
            selectedAccount.value = account
        }
    }
}