package com.infinicanvas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import io.github.gretchycat.infiniselect.FileSelectionContract
import java.util.UUID

/** A granted byte handle and its source identity travel together through recovery and confirmation. */
data class IncomingImage(val content:Uri,val authoritative:String?,val name:String?,val mime:String?,val id:String=UUID.randomUUID().toString()) {
    fun key():String=Uri.Builder().scheme("infinicanvas").authority("incoming")
        .appendQueryParameter("content",content.toString()).appendQueryParameter("source",authoritative)
        .appendQueryParameter("name",name).appendQueryParameter("mime",mime).build().toString()
    fun save()=Bundle().apply {
        putString("content",content.toString());putString("authoritative",authoritative)
        putString("name",name);putString("mime",mime);putString("id",id)
    }
    companion object {
        fun restore(bundle:Bundle)=IncomingImage(Uri.parse(bundle.getString("content")!!),bundle.getString("authoritative"),
            bundle.getString("name"),bundle.getString("mime"),bundle.getString("id") ?: UUID.randomUUID().toString())
        fun accepts(intent:Intent?)=intent?.action in setOf(Intent.ACTION_VIEW,Intent.ACTION_EDIT,Intent.ACTION_SEND)
        fun read(intent:Intent):IncomingImage {
            require(accepts(intent))
            val stream=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)
            val clip=intent.clipData?.let { if(it.itemCount>0) it.getItemAt(0).uri else null }
            val contract=intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_URI)?.let(Uri::parse)
            val uri=if(intent.action==Intent.ACTION_SEND) stream ?: intent.data ?: clip ?: contract
                else intent.data ?: stream ?: clip ?: contract
            requireNotNull(uri) { "No image file was supplied. Choose Open with on an image in Files." }
            require(uri.scheme?.lowercase() in setOf("content","file")) { "The sender must provide a readable content or file URI." }
            val source=intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_AUTHORITATIVE_URI)?.takeIf { it.isNotBlank() }
            val name=intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_NAME)?.takeIf { it.isNotBlank() }
                ?: intent.getStringExtra("displayName")?.takeIf { it.isNotBlank() }
                ?: intent.clipData?.description?.label?.toString()?.takeIf { it.isNotBlank() }
            return IncomingImage(uri,source,name,intent.type?.takeUnless { it.isBlank() || it=="*/*" || it=="application/octet-stream" })
        }
    }
}
