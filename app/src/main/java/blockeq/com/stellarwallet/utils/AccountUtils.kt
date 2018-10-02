package blockeq.com.stellarwallet.utils

import blockeq.com.stellarwallet.WalletApplication
import blockeq.com.stellarwallet.helpers.Constants

class AccountUtils {

    companion object {

        fun getBalance(type : String) : String {
            WalletApplication.localStore!!.balances!!.forEach {
                if (it.assetType == type) {
                    return StringFormat.truncateDecimalPlaces(it.balance)
                } else if (it.assetCode == type) {
                    return StringFormat.truncateDecimalPlaces(it.balance)
                }
            }
            return Constants.DEFAULT_ACCOUNT_BALANCE
        }
    }
}