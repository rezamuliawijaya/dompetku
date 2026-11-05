package com.dompetku.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    routing {
        get("/health") {
            call.respondText("OK DompetKu sync server")
        }

        // Membuat ruang data baru, mengembalikan token untuk klien
        post("/api/v1/ledger") {
            val req = call.receive<CreateLedgerRequest>()
            call.respond(Db.createLedger(req.name))
        }

        post("/api/v1/sync") {
            val token = call.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim().orEmpty()
            if (token.isEmpty() || !Db.ledgerExists(token)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token tidak valid"))
                return@post
            }
            val req = call.receive<SyncRequest>()

            // simpan perubahan yang didorong klien
            Db.upsertAccounts(token, req.changes.accounts)
            Db.upsertCategories(token, req.changes.categories)
            Db.upsertTransactions(token, req.changes.transactions)

            // kembalikan semua baris yang berubah sejak waktu tarik klien sebelumnya
            val records = Changes(
                accounts = Db.pullAccounts(token, req.since),
                categories = Db.pullCategories(token, req.since),
                transactions = Db.pullTransactions(token, req.since)
            )
            call.respond(SyncResponse(records = records, serverTime = System.currentTimeMillis()))
        }
    }
}
