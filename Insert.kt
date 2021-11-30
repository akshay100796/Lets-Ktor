
// ============================================ Insert Single Object in Database coming from client ========================================


@Serializable
data class Person(var id:Int,var name: String,var email:String,var mobile:Long)

@Serializable
data class CommonResponse(var statusCode: Int, var message:String, var data: List<Person>?)

//Create table for inserting data into database
object PersonTable : Table<Nothing>(tableName = "person"){
    val id = int("id").primaryKey()
    val name = varchar("name")
    val email = varchar("email")
    val mobile = long("mobile")
}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private var database: Database? = null

fun Application.module() {

    install(ContentNegotiation){
         json(Json {
             this.prettyPrint = true
             this.isLenient = true
         })
    }

    routing {
        dataRouting()
    }
}


fun getDatabase() : Database? {
    database = if(database == null){
        Database.connect(
            url = "jdbc:mysql://localhost/test_ktorm",
            driver = "com.mysql.cj.jdbc.Driver",
            user = "root",
            password = "")
    }else database
    return database
}
fun Routing.dataRouting(){

    post("/add"){
        val person = call.receive<Person>()
        getDatabase()?.insert(PersonTable){
            set(it.name,person.name)
            set(it.email,person.email)
            set(it.mobile,person.mobile)
        }
        call.respond(CommonResponse(statusCode = 200, message = "Person ${person.name} Added in Database",null))
    }
}
