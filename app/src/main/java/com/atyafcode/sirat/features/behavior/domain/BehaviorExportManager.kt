package com.atyafcode.sirat.features.behavior.domain

import android.content.Context
import android.net.Uri
import com.atyafcode.sirat.data.repository.BehaviorLog
import com.atyafcode.sirat.data.repository.BehaviorRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class BehaviorExportManager(private val context: Context, private val repository: BehaviorRepository) {

    fun exportToCsv(uri: Uri): Boolean {
        return try {
            val logs = repository.getAllLogs()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                    // Add UTF-8 BOM for Excel compatibility with Arabic characters
                    writer.write("\uFEFF")
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
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    // Read header and strip BOM if present
                    val firstLine = reader.readLine() ?: return@use
                    
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if (currentLine.isBlank()) continue
                        
                        val parts = parseCsvLine(currentLine)
                        if (parts.size >= 2) {
                            try {
                                val date = LocalDate.parse(parts[0].trim().removePrefix("\uFEFF"))
                                val count = parts[1].trim().toInt()
                                val reason = if (parts.size > 2) parts[2].trim() else ""
                                logs.add(BehaviorLog(date, count, reason))
                            } catch (_: Exception) {
                                // Skip invalid lines
                            }
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
