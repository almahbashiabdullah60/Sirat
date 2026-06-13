package com.atyafcode.sirat.features.behavior.domain

import android.content.Context
import android.net.Uri
import com.atyafcode.sirat.data.repository.BehaviorLog
import com.atyafcode.sirat.data.repository.BehaviorRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate

class BehaviorExportManager(private val context: Context, private val repository: BehaviorRepository) {

    fun exportToCsv(uri: Uri): Boolean {
        return try {
            val logs = repository.getAllLogs()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    // Header
                    writer.write("Date,Count,Reason\n")
                    // Data
                    logs.forEach { log ->
                        val escapedReason = log.reason.replace("\"", "\"\"")
                        writer.write("${log.date},${log.count},\"$escapedReason\"\n")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromCsv(uri: Uri): Result<Int> {
        return try {
            val logs = mutableListOf<BehaviorLog>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val header = reader.readLine() // Skip header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = parseCsvLine(line!!)
                        if (parts.size >= 2) {
                            val date = LocalDate.parse(parts[0])
                            val count = parts[1].toInt()
                            val reason = if (parts.size > 2) parts[2] else ""
                            logs.add(BehaviorLog(date, count, reason))
                        }
                    }
                }
            }
            if (logs.isNotEmpty()) {
                repository.saveMultipleLogs(logs)
                Result.success(logs.size)
            } else {
                Result.failure(Exception("No valid data found"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
