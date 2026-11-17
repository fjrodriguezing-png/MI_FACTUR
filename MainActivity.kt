package com.francisco.mifactur

import android.app.*
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.os.Bundle
import android.print.*
import android.view.ViewGroup
import android.widget.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var content: LinearLayout
    private val blue=Color.rgb(11,107,203)
    override fun onCreate(b:Bundle?){ super.onCreate(b); db=Db(this); home() }
    private fun base(title:String):LinearLayout{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,28,24,20)}
        root.addView(TextView(this).apply{text="MI_FACTUR";textSize=28f;setTextColor(blue);setPadding(0,0,0,4)})
        root.addView(TextView(this).apply{text="Tu facturación de autónomo, más fácil";textSize=15f;setPadding(0,0,0,18)})
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Inicio","Clientes","Facturas").forEach{n->nav.addView(Button(this).apply{text=n;setOnClickListener{when(n){"Inicio"->home();"Clientes"->clients();else->invoices()}}},LinearLayout.LayoutParams(0,-2,1f))}
        root.addView(nav);root.addView(TextView(this).apply{text=title;textSize=22f;setPadding(0,18,0,10)});content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val scroll=ScrollView(this).apply{addView(content)};root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));return root
    }
    private fun home(){setContentView(base("Resumen"));val invoices=db.invoices();val total=invoices.fold(BigDecimal.ZERO){a,i->a+i.total}.setScale(2,RoundingMode.HALF_UP);content.addView(TextView(this).apply{text="Clientes: ${db.clients().size}\nFacturas: ${invoices.size}\nImporte facturado: ${money(total)}";textSize=22f;setPadding(22,22,22,22);setBackgroundColor(Color.rgb(234,244,255))});content.addView(btn("+ Nuevo cliente"){clientDialog()});content.addView(btn("+ Nueva factura"){invoiceDialog()})}
    private fun clients(){setContentView(base("Clientes"));content.addView(btn("+ Nuevo cliente"){clientDialog()});db.clients().forEach{c->content.addView(TextView(this).apply{text="${c.name}\nNIF: ${c.nif}";textSize=17f;setPadding(14,14,14,14)})}}
    private fun invoices(){setContentView(base("Facturas"));content.addView(btn("+ Nueva factura"){invoiceDialog()});db.invoices().forEach{i->content.addView(TextView(this).apply{text="${i.number} · ${i.date}\n${i.client}\nBase ${money(i.base)} · IVA ${money(i.vat)} · IRPF -${money(i.irpf)}\nTOTAL ${money(i.total)}";textSize=16f;setPadding(14,14,14,14)})}}
    private fun clientDialog(){val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,8,28,0)};val n=edit("Nombre / razón social");val nif=edit("NIF/CIF");l.addView(n);l.addView(nif);AlertDialog.Builder(this).setTitle("Nuevo cliente").setView(l).setPositiveButton("Guardar"){_,_->if(n.text.isBlank())toast("Indica el nombre") else {db.addClient(n.text.toString(),nif.text.toString());clients()}}.setNegativeButton("Cancelar",null).show()}
    private fun invoiceDialog(){val cs=db.clients();if(cs.isEmpty()){toast("Primero crea un cliente");return};val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,8,28,0)};val sp=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,cs.map{it.name})};val desc=edit("Concepto");val qty=edit("Cantidad").apply{setText("1")};val price=edit("Precio unitario");val iva=edit("IVA %").apply{setText("21")};val irpf=edit("IRPF %").apply{setText("15")};listOf(sp,desc,qty,price,iva,irpf).forEach{l.addView(it)};AlertDialog.Builder(this).setTitle("Nueva factura").setView(l).setPositiveButton("Guardar"){_,_->try{db.addInvoice(cs[sp.selectedItemPosition],desc.text.toString(),qty.text.toString(),price.text.toString(),iva.text.toString(),irpf.text.toString());invoices()}catch(e:Exception){toast("Revisa cantidades e importes")}}.setNegativeButton("Cancelar",null).show()}
    private fun edit(h:String)=EditText(this).apply{hint=h;setPadding(12,12,12,12)}
    private fun btn(t:String,a:()->Unit)=Button(this).apply{text=t;setOnClickListener{a()}}
    private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
    private fun money(v:BigDecimal)=v.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')+" €"
}
data class Client(val id:Long,val name:String,val nif:String)
data class Invoice(val id:Long,val number:String,val date:String,val client:String,val base:BigDecimal,val vat:BigDecimal,val irpf:BigDecimal,val total:BigDecimal)
class Db(c:Context):SQLiteOpenHelper(c,"mifactur.db",null,1){
 override fun onCreate(d:SQLiteDatabase){d.execSQL("CREATE TABLE clients(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,nif TEXT)");d.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,number TEXT,date TEXT,client TEXT,base TEXT,vat TEXT,irpf TEXT,total TEXT)")};override fun onUpgrade(d:SQLiteDatabase,a:Int,b:Int){}
 fun addClient(n:String,nif:String)=writableDatabase.execSQL("INSERT INTO clients(name,nif) VALUES(?,?)",arrayOf(n,nif))
 fun clients():List<Client>{val r=mutableListOf<Client>();readableDatabase.rawQuery("SELECT id,name,nif FROM clients ORDER BY name",null).use{while(it.moveToNext())r+=Client(it.getLong(0),it.getString(1),it.getString(2)?:"")};return r}
 fun addInvoice(c:Client,desc:String,q:String,p:String,v:String,r:String){val qty=BigDecimal(q.replace(',','.'));val price=BigDecimal(p.replace(',','.'));val rate=BigDecimal(v.replace(',','.'));val rr=BigDecimal(r.replace(',','.'));val base=qty.multiply(price).setScale(2,RoundingMode.HALF_UP);val vat=base.multiply(rate).divide(BigDecimal("100"),2,RoundingMode.HALF_UP);val irpf=base.multiply(rr).divide(BigDecimal("100"),2,RoundingMode.HALF_UP);val total=base.add(vat).subtract(irpf).setScale(2,RoundingMode.HALF_UP);val no="F-${Calendar.getInstance().get(Calendar.YEAR)}-${String.format("%03d",invoices().size+1)}";val date=SimpleDateFormat("dd/MM/yyyy",Locale("es","ES")).format(Date());writableDatabase.execSQL("INSERT INTO invoices(number,date,client,base,vat,irpf,total) VALUES(?,?,?,?,?,?,?)",arrayOf(no,date,c.name,base.toPlainString(),vat.toPlainString(),irpf.toPlainString(),total.toPlainString()))}
 fun invoices():List<Invoice>{val r=mutableListOf<Invoice>();readableDatabase.rawQuery("SELECT id,number,date,client,base,vat,irpf,total FROM invoices ORDER BY id DESC",null).use{while(it.moveToNext())r+=Invoice(it.getLong(0),it.getString(1),it.getString(2),it.getString(3),BigDecimal(it.getString(4)),BigDecimal(it.getString(5)),BigDecimal(it.getString(6)),BigDecimal(it.getString(7)))};return r}
}
