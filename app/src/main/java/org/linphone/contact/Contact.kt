import org.linphone.activities.main.contact.data.NumberOrAddressEditorData

data class LinphoneContact(
    val id: String,
    val dahili_no: List<NumberOrAddressEditorData>,
    val as_dahili: String,
    val user_adi: String,
    val gsm: List<NumberOrAddressEditorData>,
    val email: String,
    val statu: String?
)

data class Contact(
    val id: String,
    val dahili_no: String,
    val as_dahili: String,
    val user_adi: String,
    val gsm: String,
    val email: String,
    val statu: String?
)

data class ApiResponse(
    val type: String,
    val desc: String,
    val values: List<Contact>
)
