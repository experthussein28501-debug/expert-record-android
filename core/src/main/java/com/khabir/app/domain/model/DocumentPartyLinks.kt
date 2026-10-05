package com.khabir.app.domain.model

import java.util.Base64

data class DocumentPartyLink(val name:String,val role:String)

/** Per-report metadata: full people are never put in the global style memory. */
object DocumentPartyLinks {
    const val KEY="_document_case_parties_v1"
    fun fromExamination(document:ExaminedDocument,caseNo:String,caseYear:String,caseCourt:String):List<DocumentPartyLink> {
        if(document.kind !in setOf(ExamDocumentKind.PETITION,ExamDocumentKind.JUDGMENT) || caseNo.isBlank() || caseYear.isBlank()) return emptyList()
        if(caseCourt.isBlank() || document.fields["المحكمة"].isNullOrBlank() || legalNormalize(document.fields["المحكمة"].orEmpty()) != legalNormalize(caseCourt)) return emptyList()
        if(legalNormalize(document.fields["رقم الدعوى"].orEmpty())!=legalNormalize(caseNo) || legalNormalize(document.fields["سنة الدعوى"].orEmpty())!=legalNormalize(caseYear)) return emptyList()
        return listOf("المدعون" to "المدعي","المدعى عليهم" to "المدعى عليه").flatMap { (key,role) ->
            document.fields[key].orEmpty().split(Regex("[،,;؛|\\n]+" )).filter(String::isNotBlank).map { DocumentPartyLink(it.trim(),role) }
        }
    }
    fun fromParties(parties:List<Party>):List<DocumentPartyLink> = parties.map { DocumentPartyLink(it.fullName,it.role.arabicLabel) }
    fun encode(links:List<DocumentPartyLink>):String = links.distinct().joinToString("\n") { enc(it.name)+"|"+enc(it.role) }
    fun decode(value:String?):List<DocumentPartyLink> = value.orEmpty().lines().mapNotNull { runCatching {
        val p=it.split('|');require(p.size==2);DocumentPartyLink(dec(p[0]),dec(p[1]))
    }.getOrNull() }
    private fun enc(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun dec(s:String)=String(Base64.getDecoder().decode(s),Charsets.UTF_8)
    private fun normalize(s:String)=legalNormalize(s).replace(Regex("\\s+")," ").trim()
    fun role(name:String,links:List<DocumentPartyLink>):String? = links.filter { normalize(it.name)==normalize(name) }.map { when(legalNormalize(it.role)) {
        "مدعي", "المدعي" -> "المدعي"
        "مدعي عليه", "المدعي عليه" -> "المدعى عليه"
        else -> it.role
    } }.distinct().singleOrNull()
    fun annotateAllocations(text:String,links:List<DocumentPartyLink>):String = text.lines().joinToString("\n") { line ->
        val source=line.trimStart().removePrefix("الاسم:").trimStart()
        val matched=links.map { it.name }.distinct().filter { name ->
            val n=normalize(name);val value=normalize(source)
            value.startsWith(n) && value.drop(n.length).trimStart().let { it.isEmpty() || Regex("^(?:[:،|؛]|اختص(?:ت)?(?:\\s|$)|نصيبه(?:\\s|$)|نصيب(?:\\s|$)|الت?(?:\\s|$)|مساح)").containsMatchIn(it) }
        }
        val name=matched.singleOrNull()
        val role=name?.let { role(it,links) }
        if(name!=null && role!=null && !line.contains("($role)")) line.replaceFirst(name,"$name ($role)") else line
    }
    fun link(document:ExaminedDocument,links:List<DocumentPartyLink>):ExaminedDocument {
        if(document.kind!=ExamDocumentKind.PARTITION) return document
        val allocation=document.fields["اختصاصات القسمة"] ?: return document
        return document.copy(fields=document.fields+("اختصاصات القسمة" to annotateAllocations(allocation,links)))
    }
}
