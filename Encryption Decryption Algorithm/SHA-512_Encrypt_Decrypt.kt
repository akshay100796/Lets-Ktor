package com.codexdroid

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import java.math.BigInteger
import java.security.*
import java.sql.SQLIntegrityConstraintViolationException

//This is One-Way Encryption Algorithm
object SHA512Security{

     fun encryptPassword(password: String): String{
         val messageDigest = MessageDigest.getInstance("SHA-512")
         val byteArray = messageDigest.digest(password.toByteArray())
         val bigInteger = BigInteger(1,byteArray)

         var encryptedText = bigInteger.toString(16)
         while (encryptedText.length < 32){
             encryptedText += "7$encryptedText"
         }
         return encryptedText
     }
}

@Serializable
data class Login(var id:Int,var username: String, var password: String,var decryptedPassword: String)

@Serializable
data class CommonResponse(var statusCode: Int, var message:String, val data:MutableList<Login>?)

//Create table for inserting data into database
object LoginTable : Table<Nothing>(tableName = "login"){
    val id = int("id").primaryKey()
    val username = varchar("username")  //column name in database table 'login'
    val password = varchar("password") //column name in database table 'login'
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

    val loginList = arrayListOf<Login>()

    post("/insert"){
        val login = call.receive<Login>()

        try {
            val affectedRows = getDatabase()?.insert(LoginTable){
                set(LoginTable.id,login.id)
                set(LoginTable.username,login.username)
                set(LoginTable.password,SHA512Security.encryptPassword(login.password))
            }

            if(affectedRows == 1){
                call.respond(CommonResponse(statusCode = HttpStatusCode.OK.value, "Data inserted", null))
            }else{
                call.respond(CommonResponse(statusCode = HttpStatusCode.BadRequest.value, "Error to insert Data", null))
            }
        }catch (ex : Exception){
            when (ex){
                is SQLIntegrityConstraintViolationException -> { call.respond(CommonResponse(statusCode = HttpStatusCode.Conflict.value, "ID ${login.id} already exists, pls try different", null))  }
            }
        }
    }

    post("/validate"){

        loginList.clear()
        val login = call.receive<Login>()
        val loginResponse = getDatabase()?.from(LoginTable)?.select()?.where{ LoginTable.id eq login.id }?.map {
            Login(
                id = it[LoginTable.id]!!,
                username = it[LoginTable.username]!!,
                //get client password encrypt it == get database password , if true return client plain text password else encrypted pass
                password = it[LoginTable.password]!!,
                decryptedPassword = if (it[LoginTable.password]!! == SHA512Security.encryptPassword(login.password)) login.password else it[LoginTable.password]!!.toString()
            )
        }?.firstOrNull()

        loginResponse?.let {
            loginList.add(loginResponse)
        }


        if(loginList.isNotEmpty()){
            call.respond(
                CommonResponse(
                    statusCode = HttpStatusCode.OK.value,
                    message = "${loginList.size} Person(s) Found",
                    loginList
                )
            )
        }else{
            call.respond(
                CommonResponse(
                    statusCode = HttpStatusCode.NotFound.value,
                    message = "No Person(s) Found",
                    null
                )
            )
        }
    }
}
