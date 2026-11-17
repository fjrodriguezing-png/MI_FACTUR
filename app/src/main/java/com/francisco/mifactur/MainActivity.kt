package com.francisco.mifactur
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val base=BigDecimal("100.00"); val iva=base.multiply(BigDecimal("21")).divide(BigDecimal("100"),2,RoundingMode.HALF_UP); val irpf=base.multiply(BigDecimal("15")).divide(BigDecimal("100"),2,RoundingMode.HALF_UP); val total=base.add(iva).subtract(irpf).setScale(2,RoundingMode.HALF_UP)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,60,40,40);setBackgroundColor(Color.WHITE)}
  root.addView(TextView(this).apply{text="MI_FACTUR";textSize=30f;setTextColor(Color.rgb(11,107,203))})
  root.addView(TextView(this).apply{text="Primera versión estable\n\nClientes y facturas\nLíneas múltiples\nIVA e IRPF\nRedondeo fiscal BigDecimal\n\nPrueba: 100,00 + 21% IVA - 15% IRPF = ${total.toPlainString()} €";textSize=18f;setPadding(0,30,0,0)})
  setContentView(root)
 }
}
