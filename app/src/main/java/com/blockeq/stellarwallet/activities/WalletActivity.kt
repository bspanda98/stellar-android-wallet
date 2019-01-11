package com.blockeq.stellarwallet.activities

import android.os.Bundle
import android.os.Handler
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.Fragment
import android.view.View
import com.blockeq.stellarwallet.R
import com.blockeq.stellarwallet.WalletApplication
import com.blockeq.stellarwallet.fragments.ContactsFragment
import com.blockeq.stellarwallet.fragments.SettingsFragment
import com.blockeq.stellarwallet.fragments.TradingFragment
import com.blockeq.stellarwallet.fragments.WalletFragment
import com.blockeq.stellarwallet.interfaces.OnLoadAccount
import com.blockeq.stellarwallet.interfaces.OnLoadEffects
import com.blockeq.stellarwallet.models.MinimumBalance
import com.blockeq.stellarwallet.remote.Horizon
import com.blockeq.stellarwallet.utils.AccountUtils
import com.blockeq.stellarwallet.utils.KeyboardUtils
import com.blockeq.stellarwallet.utils.NetworkUtils
import org.stellar.sdk.requests.ErrorResponse
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.effects.EffectResponse
import timber.log.Timber
import java.util.*

class WalletActivity : BaseActivity(), OnLoadAccount, OnLoadEffects, KeyboardUtils.SoftKeyboardToggleListener {
    private enum class WalletFragmentType {
        WALLET,
        TRADING,
        CONTACTS,
        SETTING
    }

    private lateinit var bottomNavigation : BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        setupUI()

    }

    private fun getReusedFragment(tag:String) : Fragment? {
       val fragment = supportFragmentManager.findFragmentByTag(tag)
       if (fragment != null) {
           Timber.d("reused a cached fragment {$tag}")
       }
       return fragment
    }

    //region Navigation

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_wallet -> {
                val walletFragment = getReusedFragment(WalletFragmentType.WALLET.name) ?: WalletFragment.newInstance()
                replaceFragment(walletFragment, WalletFragmentType.WALLET)
            }
            R.id.nav_trading -> {
                val balances = WalletApplication.wallet.getBalances()
                if (!balances.isEmpty()) {
                    val tradingFragment = getReusedFragment(WalletFragmentType.TRADING.name) ?: TradingFragment.newInstance()
                    replaceFragment(tradingFragment, WalletFragmentType.TRADING)
                }
            }
            R.id.nav_contacts -> {
                replaceFragment(getReusedFragment(WalletFragmentType.CONTACTS.name) ?: ContactsFragment(), WalletFragmentType.CONTACTS)
            }
            R.id.nav_settings -> {
                val settingsFragment = getReusedFragment(WalletFragmentType.SETTING.name) ?: SettingsFragment.newInstance()
                replaceFragment(settingsFragment, WalletFragmentType.SETTING)
            }
            else -> throw IllegalAccessException("Navigation item not supported $item.title(${item.itemId})")
        }
        return@OnNavigationItemSelectedListener true
    }

    private fun replaceFragment(fragment: Fragment, type : WalletFragmentType) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.content_container, fragment, type.name)
        //This is complete necessary to be able to reuse the fragments using the supportFragmentManager
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun setupUI() {
        bottomNavigation = findViewById(R.id.navigationView)
        bottomNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        bottomNavigation.selectedItemId = R.id.nav_wallet
    }

    //endregion

    override fun onResume() {
        super.onResume()
        startPollingAccount()

        KeyboardUtils.addKeyboardToggleListener(this, this)
    }

    override fun onPause() {
        super.onPause()
        endPollingAccount()

        KeyboardUtils.removeKeyboardToggleListener(this)

    }

    /**
     * When the keyboard is opened the bottomNavigation gets pushed up.
     */
    override fun onToggleSoftKeyboard(isVisible: Boolean) {
        if (isVisible) {
            bottomNavigation.visibility = View.GONE
        } else {
            bottomNavigation.visibility = View.VISIBLE
        }
    }

    private var handler = Handler()
    private var runnableCode : Runnable? = null

    //TODO polling for only non-created accounts on Stellar.
    private fun startPollingAccount() {
        runnableCode = object : Runnable {
            override fun run() {

                if (NetworkUtils(applicationContext).isNetworkAvailable()) {

                    Horizon.getLoadAccountTask(this@WalletActivity)
                            .execute()

                    Horizon.getLoadEffectsTask(this@WalletActivity)
                            .execute()
                } else {
                    NetworkUtils(applicationContext).displayNoNetwork()
                }

                handler.postDelayed(this, 5000)
            }
        }

        handler.post(runnableCode)
    }

    private fun endPollingAccount() {
        handler.removeCallbacks(runnableCode)
    }

    override fun onLoadAccount(result: AccountResponse?) {
        if (result != null) {
            WalletApplication.wallet.setBalances(result.balances)
            WalletApplication.userSession.minimumBalance = MinimumBalance(result)
            WalletApplication.wallet.setAvailableBalance(AccountUtils.calculateAvailableBalance())

            bottomNavigation.menu.getItem(WalletFragmentType.TRADING.ordinal).isEnabled = true
        }

        val fragment = supportFragmentManager.findFragmentByTag(WalletFragmentType.WALLET.name)
        if (fragment != null) {
            (fragment as WalletFragment).onLoadAccount(result)
        }
    }

    override fun onError(error: ErrorResponse) {
        val fragment = supportFragmentManager.findFragmentByTag(WalletFragmentType.WALLET.name)
        if (fragment != null) {
            (fragment as WalletFragment).onError(error)
        }
    }

    override fun onLoadEffects(result: ArrayList<EffectResponse>?) {
        val fragment = supportFragmentManager.findFragmentByTag(WalletFragmentType.WALLET.name)
        if (fragment != null) {
            (fragment as WalletFragment).onLoadEffects(result)
        }
    }
}
