package cz.skala.trezorwallet.ui.addressdetail

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import cz.skala.trezorwallet.R
import cz.skala.trezorwallet.TrezorApplication
import cz.skala.trezorwallet.data.AppDatabase
import cz.skala.trezorwallet.data.entity.Account
import cz.skala.trezorwallet.data.entity.Address
import cz.skala.trezorwallet.data.repository.AccountRepository
import cz.skala.trezorwallet.labeling.LabelingManager
import cz.skala.trezorwallet.ui.BaseViewModel
import cz.skala.trezorwallet.ui.SingleLiveEvent
import kotlinx.coroutines.launch
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance

class AddressDetailViewModel(app: Application) : BaseViewModel(app), KodeinAware {
    private val accountRepository: AccountRepository by instance()
    private val labeling: LabelingManager by instance()

    val addressLabel = MutableLiveData<String>()
    val accountLabel = MutableLiveData<String>()
    val derivationPath = MutableLiveData<String>()
    val showOnTrezorRequest = SingleLiveEvent<Pair<Address, Account>>()

    private lateinit var address: Address
    private lateinit var account: Account

    private val resources = getApplication<TrezorApplication>().resources

    fun start(address: Address) {
        this.address = address
        loadAccount()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            account = accountRepository.getById(address.account)
            updateAddressLabel()
            accountLabel.value = account.getDisplayLabel(resources)
            derivationPath.value = address.getPathString(account)
        }
    }

    fun setAddressLabel(label: String) = viewModelScope.launch {
        labeling.setAddressLabel(address, label)
        updateAddressLabel()
    }

    fun showOnTrezor() {
        showOnTrezorRequest.value = Pair(address, account)
    }

    private fun updateAddressLabel() {
        addressLabel.value = if (address.label.isNullOrEmpty())
            resources.getString(R.string.address) else address.label
    }
}