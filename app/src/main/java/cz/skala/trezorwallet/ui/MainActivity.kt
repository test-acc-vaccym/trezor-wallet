package cz.skala.trezorwallet.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.android.AppCompatActivityInjector
import com.satoshilabs.trezor.intents.ui.activity.TrezorActivity
import com.satoshilabs.trezor.intents.ui.data.GetPublicKeyResult
import cz.skala.trezorwallet.R
import cz.skala.trezorwallet.TrezorApplication
import cz.skala.trezorwallet.data.AppDatabase
import cz.skala.trezorwallet.data.entity.Account
import cz.skala.trezorwallet.data.item.AccountItem
import cz.skala.trezorwallet.data.item.AccountSectionItem
import cz.skala.trezorwallet.data.item.AddAccountItem
import cz.skala.trezorwallet.data.item.Item
import cz.skala.trezorwallet.ui.addresses.AddressesFragment
import cz.skala.trezorwallet.ui.getstarted.GetStartedActivity
import cz.skala.trezorwallet.ui.send.SendFragment
import cz.skala.trezorwallet.ui.transactions.TransactionsFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.jetbrains.anko.defaultSharedPreferences


class MainActivity : AppCompatActivity(), AppCompatActivityInjector {
    companion object {
        private const val TAG = "MainActivity"

        private const val ITEM_FORGET = 10
        private const val REQUEST_GET_PUBLIC_KEY = 2
    }

    override val injector = KodeinInjector()
    private val database: AppDatabase by injector.instance()
    private val viewModel: MainViewModel by injector.instance()

    private val accountsAdapter = AccountsAdapter()

    override fun provideOverridingModule() = Kodein.Module {
        bind<MainViewModel>() with provider {
            val factory = MainViewModel.Factory(instance())
            ViewModelProviders.of(this@MainActivity, factory)[MainViewModel::class.java]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initializeInjector()
        super.onCreate(savedInstanceState)

        if (!defaultSharedPreferences.getBoolean(TrezorApplication.PREF_INITIALIZED, false)) {
            startGetStartedActivity()
            return
        }

        setContentView(R.layout.activity_main)

        initToolbar()

        accountsAdapter.onItemClickListener = {
            viewModel.setSelectedAccount(it)
            drawerLayout.closeDrawers()
        }

        accountsAdapter.onAddAccountListener = {
            viewModel.addAccount(it)
        }

        accountsList.adapter = accountsAdapter
        accountsList.layoutManager = LinearLayoutManager(this)

        viewModel.accounts.observe(this, Observer {
            if (it != null) {
                if (it.isNotEmpty()) {
                    showAccounts(it)
                    val selectedAccount = viewModel.selectedAccount.value
                    if (selectedAccount == null || !it.contains(selectedAccount)) {
                        val newSelectedAccount = it.first()
                        viewModel.setSelectedAccount(newSelectedAccount)
                        accountsAdapter.selectedAccount = newSelectedAccount
                        accountsAdapter.notifyDataSetChanged()
                    }
                } else {
                    forgetDevice()
                }
            }
        })

        viewModel.selectedAccount.observe(this, Observer {
            if (it != null) {
                showSelectedAccount(it)
            }
        })

        viewModel.onTrezorRequest.observe(this, Observer {
            if (it != null) {
                val intent = TrezorActivity.createIntent(this, it)
                startActivityForResult(intent, REQUEST_GET_PUBLIC_KEY)
            }
        })

        viewModel.onLastAccountEmpty.observe(this, Observer {
            Toast.makeText(applicationContext, R.string.last_account_empty, Toast.LENGTH_LONG).show()
        })

        navigation.setOnNavigationItemSelectedListener {
            val account = viewModel.selectedAccount.value!!
            val accountId = account.id
            when (it.itemId) {
                R.id.item_transactions -> showTransactions(accountId)
                R.id.item_receive -> showAddresses(accountId)
                R.id.item_send -> showSend(accountId)
            }
            true
        }
    }

    override fun onDestroy() {
        destroyInjector()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, ITEM_FORGET, 0, R.string.forget_device)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            ITEM_FORGET -> {
                viewModel.accounts.removeObservers(this)
                forgetDevice()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_GET_PUBLIC_KEY -> if (resultCode == Activity.RESULT_OK) {
                val result = TrezorActivity.getResult(data) as GetPublicKeyResult
                val node = result.message.node
                viewModel.saveAccount(node)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun initToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionbar = supportActionBar
        actionbar!!.setDisplayHomeAsUpEnabled(true)
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
    }

    private fun forgetDevice() {
        launch(UI) {
            bg {
                database.accountDao().deleteAll()
                database.transactionDao().deleteAll()
                database.addressDao().deleteAll()
                defaultSharedPreferences.edit()
                        .putBoolean(TrezorApplication.PREF_INITIALIZED, false).apply()
            }.await()
            startGetStartedActivity()
        }
    }

    private fun startGetStartedActivity() {
        val intent = Intent(this, GetStartedActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showAccounts(accounts: List<Account>) {
        accountsAdapter.items = createAccountItems(accounts)
        accountsAdapter.notifyDataSetChanged()
    }

    private fun createAccountItems(accounts: List<Account>): List<Item> {
        val items = mutableListOf<Item>()
        var legacy = false
        for (account in accounts) {
            if (!legacy && account.legacy) {
                legacy = true
                items.add(AddAccountItem(false))
                items.add(AccountSectionItem(R.string.legacy_accounts))
            }
            items.add(AccountItem(account))
        }
        items.add(AddAccountItem(true))
        return items
    }

    fun showTransactions(accountId: String) {
        val f = TransactionsFragment()
        val args = Bundle()
        args.putString(TransactionsFragment.ARG_ACCOUNT_ID, accountId)
        f.arguments = args
        replaceFragment(f)
        navigation.selectedItemId = 0
    }

    private fun showAddresses(accountId: String) {
        val f = AddressesFragment()
        val args = Bundle()
        args.putString(AddressesFragment.ARG_ACCOUNT_ID, accountId)
        f.arguments = args
        replaceFragment(f)
    }

    private fun showSend(accountId: String) {
        val f = SendFragment()
        val args = Bundle()
        args.putString(SendFragment.ARG_ACCOUNT_ID, accountId)
        f.arguments = args
        replaceFragment(f)
    }

    private fun showSelectedAccount(account: Account) {
        showTransactions(account.id)

        if (navigation.selectedItemId != R.id.item_transactions) {
            navigation.selectedItemId = R.id.item_transactions
        }
    }

    private fun replaceFragment(f: Fragment) {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.content, f)
        ft.commit()
    }
}