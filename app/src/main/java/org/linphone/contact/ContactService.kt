import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import java.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.contact.data.NumberOrAddressEditorData
import org.linphone.contact.NativeContactEditor
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.utils.PermissionHelper

val contactList = mutableListOf<Contact>()

fun fetchContacts() {
    val client = OkHttpClient()
    val url = "https://panel.intouchtech.com.tr/api.php"
    val json = """
        {
            "action": "dahili_liste",
            "email": "c@d.com",
            "sifre": "123123*"
        }
    """.trimIndent()

    val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
    val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            response.body?.string()?.let { responseBody ->
                val apiResponse = Gson().fromJson(responseBody, ApiResponse::class.java)
                if (apiResponse.type == "success") {
                    println("Fetched contacts: ${apiResponse.values.size}")
                    Handler(Looper.getMainLooper()).post {
                        addContacts(apiResponse.values)
                    }
                    println(responseBody)
                } else {
                    println("Failed to fetch contacts: ${apiResponse.desc}")
                }
            }
        }
    })
}

fun addContacts(contacts: List<Contact>) {
    val mainHandler = Handler(Looper.getMainLooper())

    contacts.forEach { contact ->
        println("Adding contact: ${contact.user_adi}")
        //   contactList.add(contact)

        val nativeId = if (PermissionHelper.get().hasReadContactsPermission() &&
            PermissionHelper.get().hasWriteContactsPermission()
        ) {
            Log.i("[Contact Editor] Creating native contact")
            val syncAccountName = "TTSS Eco kişiler"
            val syncAccountType = "org.linphone.sync"
            NativeContactEditor.createAndroidContact(syncAccountName, syncAccountType)
                .toString()
        } else {
            Log.e("[Contact Editor] Can't create native contact, permission denied")
            null
        }

        val friend: Friend? = coreContext.core.createFriend()
        friend?.refKey = nativeId

        val gsmString = (contact.gsm.map { it.toString() }.joinToString(""))
        val gsmlist: List<NumberOrAddressEditorData> = listOf(
            NumberOrAddressEditorData(gsmString, false)
        )

        val dahiliString = contact.dahili_no.map { it.toString() }.joinToString("")
        val sipList: List<NumberOrAddressEditorData> = contact.dahili_no.map {
            NumberOrAddressEditorData(dahiliString.toString(), true)
        }

        Log.i("[Contact Editor] GSM List: ${gsmlist[0].currentValue}")
        Log.i("[Contact Editor] SIP List: ${sipList[0].currentValue}")

        if (friend != null) {
            NativeContactEditor(friend)
                .setFirstAndLastNames(
                    contact.user_adi.orEmpty(),

                    ""
                )
                .setPhoneNumbers(
                    gsmlist,
                    90
                )
                .setSipAddresses(sipList, 90)
                .commit()
        }

        mainHandler.post {
            // NativeContactEditor.createAndroidContact(contact.user_adi, null)
            // ContactEditorData(friend).save()
            //  ContactEditorData(friend = friend).save()
        }
    }
}
