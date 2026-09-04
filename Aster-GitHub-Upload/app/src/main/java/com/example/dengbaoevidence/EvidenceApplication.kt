package com.example.dengbaoevidence

import android.app.Application
import com.example.dengbaoevidence.data.DataRepository
import com.example.dengbaoevidence.data.EvidenceDatabase

class EvidenceApplication : Application() {
  val repository: DataRepository by lazy {
    DataRepository(EvidenceDatabase.getInstance(this))
  }
}

