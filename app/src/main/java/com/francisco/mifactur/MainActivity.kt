package com.francisco.mifactur

import android.app.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

private fun bd(s: String) = BigDecimal(s.replace(',', '.').ifBlank { "0" })
private fun r2(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)
private fun euro(v: BigDecimal) = r2(v).toPlainString().replace('.', ',') + " €"

data class Client(val id: Long, val name: String, val nif: String, val email: String, val street: String, val streetNumber: String, val postalCode: String, val city: String, val province: String)
data class Invoice(val id: Long, val number: String, val date: String, val client: String, val nif: String, val irpfRate: BigDecimal, val base: BigDecimal, val vat: BigDecimal, val irpf: BigDecimal, val total: BigDecimal, val status: String)
data class Line(val id: Long, val invoiceId: Long, val description: String, val qty: BigDecimal, val price: BigDecimal, val vatRate: BigDecimal, val base: BigDecimal, val vat: BigDecimal)

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var body: LinearLayout
    private var pendingBytes = ByteArray(0)

    private val blue = Color.rgb(30, 64, 175)
    private val navy = Color.rgb(15, 23, 42)
    private val slate = Color.rgb(71, 85, 105)
    private val bg = Color.rgb(244, 247, 251)
    private val green = Color.rgb(22, 163, 74)
    private val amber = Color.rgb(217, 119, 6)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        showHome()
    }

    private fun rounded(color: Int, radius: Float = 22f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shell(title: String, subtitle: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            background = rounded(Color.WHITE, 0f)
        }
        header.addView(ImageView(this).apply {
            setImageResource(com.francisco.mifactur.R.drawable.mi_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = "Logotipo MI_FACTUR"
        }, LinearLayout.LayoutParams(-1, dp(105)).apply { setMargins(0, 0, 0, dp(8)) })
        header.addView(TextView(this).apply {
            text = "MI_FACTUR"
            textSize = 29f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Tu facturación de autónomo, más fácil"
            textSize = 14f
            setTextColor(slate)
        })
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        nav.addView(navButton("⌂\nInicio") { showHome() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("♙\nClientes") { showClients() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("▤\nFacturas") { showInvoices() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        header.addView(nav)
        root.addView(header)

        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        body.addView(TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(navy)
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(slate)
            setPadding(0, dp(2), 0, dp(16))
        })
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(blue)
        setPadding(dp(4), dp(5), dp(4), dp(5))
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(blue, dp(14).toFloat())
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setOnClickListener { action() }
    }

    private fun softButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(blue)
        background = rounded(Color.rgb(239, 246, 255), dp(12).toFloat())
        setPadding(dp(12), dp(11), dp(12), dp(11))
        setOnClickListener { action() }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.WHITE, dp(18).toFloat())
        elevation = dp(2).toFloat()
    }

    private fun addCard(view: View) {
        body.addView(view, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
    }

    private fun statCard(label: String, value: String, accent: Int): LinearLayout = card().apply {
        background = rounded(when(accent) { green -> Color.rgb(240,253,244); amber -> Color.rgb(255,247,237); else -> Color.rgb(239,246,255) }, dp(20).toFloat())
        elevation = dp(3).toFloat()
        val stripe = View(this@MainActivity).apply { background = rounded(accent, dp(3).toFloat()) }
        addView(stripe, LinearLayout.LayoutParams(dp(38), dp(5)).apply { setMargins(0,0,0,dp(10)) })
        addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(slate); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@MainActivity).apply {
            text = value; textSize = 23f; setTextColor(accent); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(5), 0, 0)
        })
    }

    private fun showHome() {
        shell("Resumen", "Una vista rápida de tu negocio")
        val invoices = db.invoices()
        val total = r2(invoices.fold(BigDecimal.ZERO) { a, i -> a + i.total })
        val paid = invoices.count { it.status == "Pagada" }
        val pending = invoices.count { it.status == "Pendiente" }

        addCard(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18),dp(16),dp(18),dp(16))
            background = rounded(Color.rgb(30,64,175), dp(20).toFloat())
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply { text = "Tu facturación al día"; textSize = 19f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
                addView(TextView(this@MainActivity).apply { text = "Crea facturas, controla cobros y exporta tus documentos."; textSize = 13f; setTextColor(Color.rgb(219,234,254)); setPadding(0,dp(4),0,0) })
            }, LinearLayout.LayoutParams(0,-2,1f))
        })
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(statCard("FACTURAS", invoices.size.toString(), blue), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        row1.addView(statCard("FACTURADO", euro(total), green), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        addCard(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(statCard("PAGADAS", paid.toString(), green), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        row2.addView(statCard("PENDIENTES", pending.toString(), amber), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        addCard(row2)

        addCard(primaryButton("＋ Crear nueva factura") { invoiceDialog() })
        addCard(softButton("＋ Añadir cliente") { clientDialog() })

        if (invoices.isNotEmpty()) {
            body.addView(TextView(this).apply {
                text = "Últimas facturas"; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(8), 0, dp(10))
            })
            invoices.take(3).forEach { addCard(invoiceCard(it)) }
        }
    }

    private fun showClients() {
        shell("Clientes", "Gestiona tus clientes y datos fiscales")
        addCard(primaryButton("＋ Nuevo cliente") { clientDialog() })
        db.clients().forEach { c ->
            addCard(card().apply {
                addView(TextView(this@MainActivity).apply { text = c.name; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD) })
                addView(TextView(this@MainActivity).apply { text = "NIF/CIF  ${c.nif}\n${c.street}, ${c.streetNumber} · ${c.postalCode} ${c.city} (${c.province})\n${c.email}"; textSize = 14f; setTextColor(slate); setPadding(0, dp(6), 0, 0) })
            })
        }
    }

    private fun showInvoices() {
        shell("Facturas", "Consulta, exporta y genera documentos")
        addCard(primaryButton("＋ Nueva factura") { invoiceDialog() })
        db.invoices().forEach { addCard(invoiceCard(it)) }
    }

    private fun invoiceCard(i: Invoice): LinearLayout {
        val lines = db.lines(i.id)
        return card().apply {
            val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(this@MainActivity).apply {
                text = i.number; textSize = 17f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(TextView(this@MainActivity).apply {
                text = i.status
                textSize = 12f
                setTextColor(if (i.status == "Pagada") green else amber)
                background = rounded(if (i.status == "Pagada") Color.rgb(240,253,244) else Color.rgb(255,247,237), dp(10).toFloat())
                setPadding(dp(10), dp(5), dp(10), dp(5))
            })
            addView(top)
            addView(TextView(this@MainActivity).apply { text = "${i.date} · ${i.client}"; textSize = 14f; setTextColor(slate); setPadding(0, dp(5), 0, dp(10)) })
            lines.forEach { l ->
                addView(TextView(this@MainActivity).apply {
                    text = "${l.description}   ${l.qty.stripTrailingZeros().toPlainString()} × ${euro(l.price)}   IVA ${l.vatRate.stripTrailingZeros().toPlainString()}%"
                    textSize = 13f; setTextColor(slate); setPadding(0, dp(3), 0, dp(3))
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = "TOTAL   ${euro(i.total)}"
                textSize = 20f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(12), 0, dp(10))
            })
            val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(softButton("PDF") { savePdf(i, lines) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(5), 0) })
            actions.addView(softButton("XML") { saveXml(i, lines) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, 0, 0) })
            addView(actions)
        }
    }

    private fun clientDialog() {
        val f = form()
        val n = input("Ej.: Cooperativa Agrícola Limiana")
        val nif = input("Ej.: B12345678")
        val email = input("Ej.: administracion@cliente.es")
        val street = input("Calle / avenida, ej.: Rúa Progreso")
        val streetNumber = input("Número / piso, ej.: 24, 2º A")
        val postalCode = input("Código postal, ej.: 32630")
        val city = input("Ciudad, ej.: Xinzo de Limia")
        val province = input("Provincia, ej.: Ourense")
        listOf(n, nif, email, street, streetNumber, postalCode, city, province).forEach { f.addView(it) }
        AlertDialog.Builder(this).setTitle("Nuevo cliente").setView(ScrollView(this).apply { addView(f) })
            .setPositiveButton("Guardar") { _, _ ->
                if (n.text.isNotBlank()) {
                    db.addClient(n.text.toString(), nif.text.toString(), email.text.toString(), street.text.toString(), streetNumber.text.toString(), postalCode.text.toString(), city.text.toString(), province.text.toString())
                    showClients()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun invoiceDialog() {
        val clients = db.clients()
        if (clients.isEmpty()) { toast("Primero crea un cliente"); return }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(18)) }
        val clientLabel = TextView(this).apply { text = "1  Cliente"; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD) }
        val sp = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, clients.map { it.name }) }
        val invoiceNumber = input("Número de factura (automático o manual)", db.nextInvoiceNumber())
        root.addView(clientLabel); root.addView(sp)
        root.addView(TextView(this).apply { text = "Número de factura"; setTextColor(slate); setPadding(0, dp(10), 0, 0) })
        invoiceNumber.isEnabled = false
        invoiceNumber.setTextColor(navy)
        invoiceNumber.background = rounded(Color.rgb(248,250,252), dp(12).toFloat())
        val numberRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        numberRow.addView(invoiceNumber, LinearLayout.LayoutParams(0, dp(54), 1f))
        numberRow.addView(softButton("✎ Editar") {
            invoiceNumber.isEnabled = true
            invoiceNumber.requestFocus()
            invoiceNumber.setSelection(invoiceNumber.text.length)
            toast("Ahora puedes cambiar manualmente el número de factura")
        }, LinearLayout.LayoutParams(dp(105), dp(54)).apply { setMargins(dp(8),0,0,0) })
        root.addView(numberRow)
        root.addView(TextView(this).apply { text = "Generado automáticamente. Pulsa Editar si quieres usar otra serie o numeración."; textSize = 12f; setTextColor(slate); setPadding(0,dp(4),0,0) })

        root.addView(TextView(this).apply { text = "2  Conceptos de la factura"; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(6)) })
        val linesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val drafts = mutableListOf<Array<EditText>>()
        val summary = TextView(this).apply { textSize = 16f; setTextColor(navy); setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.rgb(239,246,255), dp(14).toFloat()) }
        val irpf = input("Ej.: IRPF 15%", "15")
        val status = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Pendiente", "Pagada", "Vencida")) }

        fun refreshSummary() {
            try {
                val valid = drafts.filter { it[0].text.isNotBlank() && it[1].text.isNotBlank() && it[2].text.isNotBlank() && it[3].text.isNotBlank() }
                val bases = valid.map { r2(bd(it[1].text.toString()) * bd(it[2].text.toString())) }
                val base = r2(bases.fold(BigDecimal.ZERO) { a,b -> a+b })
                val vat = r2(valid.mapIndexed { idx, a -> r2(bases[idx] * bd(a[3].text.toString()) / BigDecimal("100")) }.fold(BigDecimal.ZERO) { a,b -> a+b })
                val retention = r2(base * bd(irpf.text.toString()) / BigDecimal("100"))
                summary.text = "Base   ${euro(base)}\nIVA     ${euro(vat)}\nIRPF   -${euro(retention)}\n\nTOTAL   ${euro(r2(base + vat - retention))}"
            } catch (_:Exception) { summary.text = "Completa los conceptos para ver el total" }
        }

        fun addLine() {
            val card = card()
            val n = drafts.size + 1
            card.addView(TextView(this).apply { text = "Concepto $n"; textSize = 16f; setTextColor(blue); setTypeface(typeface, Typeface.BOLD) })
            val d = input("Ej.: Mantenimiento EDAR noviembre")
            val q = input("Ej.: 2", "1")
            val p = input("Ej.: 125,50 €")
            val v = input("Ej.: 21%", "21")
            listOf(d,q,p,v).forEach { e -> card.addView(e); e.setOnFocusChangeListener { _,has -> if(!has) refreshSummary() } }
            val remove = softButton("Eliminar este concepto") {
                if (drafts.size > 1) { val idx=drafts.indexOfFirst { it[0]===d }; if(idx>=0) drafts.removeAt(idx); linesBox.removeView(card); refreshSummary() }
                else toast("La factura necesita al menos un concepto")
            }
            card.addView(remove)
            drafts += arrayOf(d,q,p,v)
            linesBox.addView(card, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))})
        }
        root.addView(linesBox); addLine()
        root.addView(softButton("＋ Añadir otro concepto") { addLine() })

        root.addView(TextView(this).apply { text = "3  Impuestos y estado"; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(6)) })
        root.addView(irpf); root.addView(TextView(this).apply{text="Estado de cobro";setTextColor(slate);setPadding(0,dp(8),0,0)}); root.addView(status)
        irpf.setOnFocusChangeListener { _,has -> if(!has) refreshSummary() }
        root.addView(TextView(this).apply { text = "4  Resumen"; textSize = 18f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(6)) })
        root.addView(summary); refreshSummary()

        root.addView(primaryButton("Vista previa antes de confirmar") {
            try {
                refreshSummary()
                val clientName = clients[sp.selectedItemPosition].name
                val lineText = drafts.mapIndexed { idx,a ->
                    val desc=a[0].text.toString().ifBlank { "Concepto ${idx+1}" }; val qty=a[1].text.toString(); val price=a[2].text.toString(); val vat=a[3].text.toString()
                    "${idx+1}. $desc\n   $qty × $price € · IVA $vat%"
                }.joinToString("\n\n")
                AlertDialog.Builder(this).setTitle("Vista previa de factura").setMessage("Factura: ${invoiceNumber.text}\nCliente: $clientName\nEstado: ${status.selectedItem}\n\n$lineText\n\n${summary.text}\n\nSi algo no está bien, pulsa EDITAR y vuelve a los campos.").setPositiveButton("CONFIRMAR Y GUARDAR") { _,_ ->
                    try { val parsed=drafts.map { require(it[0].text.isNotBlank()); arrayOf(it[0].text.toString(),it[1].text.toString(),it[2].text.toString(),it[3].text.toString()) }; db.addInvoice(invoiceNumber.text.toString(),clients[sp.selectedItemPosition],bd(irpf.text.toString()),status.selectedItem.toString(),parsed); showInvoices() } catch(e:Exception) { toast("Revisa los campos antes de confirmar") }
                }.setNegativeButton("EDITAR", null).show()
            } catch(e:Exception) { toast("Completa los conceptos antes de abrir la vista previa") }
        })

        val scroll = ScrollView(this).apply { addView(root) }
        AlertDialog.Builder(this).setTitle("Crear factura").setView(scroll)
            .setPositiveButton("Cerrar") { _, _ -> }.setNegativeButton("Cancelar", null).show()
    }

    private fun qrUrl(i: Invoice) = "https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQR?nif=${enc(i.nif)}&numserie=${enc(i.number)}&fecha=${enc(i.date)}&importe=${r2(i.total).toPlainString()}"
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun qr(s: String): Bitmap {
        val m = QRCodeWriter().encode(s, BarcodeFormat.QR_CODE, 180, 180)
        return Bitmap.createBitmap(180, 180, Bitmap.Config.RGB_565).apply { for (y in 0 until 180) for (x in 0 until 180) setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE) }
    }

    private fun savePdf(i: Invoice, lines: List<Line>) {
        val doc = PdfDocument(); val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); val c = page.canvas; val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun tx(s: String, y: Float, size: Float = 12f, bold: Boolean = false) { p.color = Color.BLACK; p.textSize = size; p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; c.drawText(s, 45f, y, p) }
        val logo = BitmapFactory.decodeResource(resources, com.francisco.mifactur.R.drawable.mi_logo)
        c.drawBitmap(logo, null, RectF(415f, 18f, 550f, 112f), p)
        tx("Francisco Javier Rodríguez Araújo", 38f, 15f, true)
        tx("NIF 34987220B", 60f, 12f)
        tx("Ingeniero técnico Agrícola", 80f, 12f)
        tx("FACTURA ${i.number}", 122f, 18f, true)
        tx("Fecha: ${i.date}", 146f)
        val customer = db.clientForInvoice(i.id)
        tx("CLIENTE: ${i.client} · ${i.nif}", 178f, 13f, true)
        tx("${customer.street}, ${customer.streetNumber}", 199f)
        tx("${customer.postalCode} ${customer.city} · ${customer.province}", 218f)
        var y = 255f; lines.forEach { tx("${it.description}  ${it.qty} x ${euro(it.price)}  IVA ${it.vatRate}%", y); y += 24 }
        tx("Base: ${euro(i.base)}", y + 24); tx("IVA: ${euro(i.vat)}", y + 48); tx("IRPF (${i.irpfRate}%): -${euro(i.irpf)}", y + 72); tx("TOTAL: ${euro(i.total)}", y + 108, 19f, true)
        c.drawBitmap(qr(qrUrl(i)), null, RectF(365f, 625f, 545f, 805f), p); doc.finishPage(page)
        val file = File(cacheDir, "${i.number}.pdf"); FileOutputStream(file).use { doc.writeTo(it) }; doc.close(); saveDocument("${i.number}.pdf", file.readBytes(), "application/pdf")
    }

    private fun saveXml(i: Invoice, lines: List<Line>) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val rows = lines.joinToString("") { "<Linea><Descripcion>${esc(it.description)}</Descripcion><Cantidad>${it.qty}</Cantidad><Precio>${r2(it.price)}</Precio><IVA>${it.vatRate}</IVA><Base>${r2(it.base)}</Base><CuotaIVA>${r2(it.vat)}</CuotaIVA></Linea>" }
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Factura><Numero>${esc(i.number)}</Numero><Fecha>${esc(i.date)}</Fecha><Cliente>${esc(i.client)}</Cliente><NIF>${esc(i.nif)}</NIF><Lineas>$rows</Lineas><Base>${r2(i.base)}</Base><IVA>${r2(i.vat)}</IVA><IRPF>${r2(i.irpf)}</IRPF><Total>${r2(i.total)}</Total><QR>${esc(qrUrl(i))}</QR></Factura>"
        saveDocument("${i.number}.xml", xml.toByteArray(Charsets.UTF_8), "application/xml")
    }

    private fun saveDocument(name: String, data: ByteArray, type: String) { pendingBytes = data; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type; putExtra(Intent.EXTRA_TITLE, name) }, 77) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 77 && resultCode == RESULT_OK) data?.data?.let { uri -> contentResolver.openOutputStream(uri)?.use { it.write(pendingBytes) }; toast("Archivo guardado") } }

    private fun form() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
    private fun input(hintText: String, value: String = "") = EditText(this).apply { hint = hintText; setText(value); setPadding(dp(12), dp(10), dp(12), dp(10)) }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}

class Db(c: Context) : SQLiteOpenHelper(c, "mifactur_visual.db", null, 2) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE clients(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,nif TEXT,email TEXT,street TEXT,street_number TEXT,postal_code TEXT,city TEXT,province TEXT)")
        d.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,number TEXT,date TEXT,client_id INTEGER,irpf_rate TEXT,base TEXT,vat TEXT,irpf TEXT,total TEXT,status TEXT)")
        d.execSQL("CREATE TABLE lines(id INTEGER PRIMARY KEY AUTOINCREMENT,invoice_id INTEGER,description TEXT,qty TEXT,price TEXT,vat_rate TEXT,base TEXT,vat TEXT)")
    }
    override fun onUpgrade(d: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("street", "street_number", "postal_code", "city", "province").forEach { column ->
                d.execSQL("ALTER TABLE clients ADD COLUMN $column TEXT DEFAULT ''")
            }
        }
    }
    fun addClient(n: String, nif: String, email: String, street: String, streetNumber: String, postalCode: String, city: String, province: String) { writableDatabase.execSQL("INSERT INTO clients(name,nif,email,street,street_number,postal_code,city,province) VALUES(?,?,?,?,?,?,?,?)", arrayOf(n,nif,email,street,streetNumber,postalCode,city,province)) }
    fun clients(): List<Client> { val out = mutableListOf<Client>(); readableDatabase.rawQuery("SELECT id,name,nif,email,street,street_number,postal_code,city,province FROM clients ORDER BY name", null).use { while (it.moveToNext()) out += Client(it.getLong(0),it.getString(1),it.getString(2)?:"",it.getString(3)?:"",it.getString(4)?:"",it.getString(5)?:"",it.getString(6)?:"",it.getString(7)?:"",it.getString(8)?:"") }; return out }
    fun addInvoice(manualNumber: String, c: Client, irpfRate: BigDecimal, status: String, raw: List<Array<String>>) {
        val parsed = raw.map { a -> require(a[0].isNotBlank()); val q = bd(a[1]); val p = bd(a[2]); val vr = bd(a[3]); require(q > BigDecimal.ZERO && p >= BigDecimal.ZERO); val b = r2(q * p); val vat = r2(b * vr / BigDecimal("100")); arrayOf(a[0], q.toPlainString(), p.toPlainString(), vr.toPlainString(), b.toPlainString(), vat.toPlainString()) }
        val base = r2(parsed.fold(BigDecimal.ZERO) { a, l -> a + bd(l[4]) }); val vat = r2(parsed.fold(BigDecimal.ZERO) { a, l -> a + bd(l[5]) }); val irpf = r2(base * irpfRate / BigDecimal("100")); val total = r2(base + vat - irpf)
        val number = manualNumber.trim().ifBlank { nextInvoiceNumber() }; val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date()); val d = writableDatabase; d.beginTransaction()
        try { val cv = ContentValues().apply { put("number", number); put("date", date); put("client_id", c.id); put("irpf_rate", irpfRate.toPlainString()); put("base", base.toPlainString()); put("vat", vat.toPlainString()); put("irpf", irpf.toPlainString()); put("total", total.toPlainString()); put("status", status) }; val id = d.insertOrThrow("invoices", null, cv); parsed.forEach { l -> d.execSQL("INSERT INTO lines(invoice_id,description,qty,price,vat_rate,base,vat) VALUES(?,?,?,?,?,?,?)", arrayOf(id, l[0], l[1], l[2], l[3], l[4], l[5])) }; d.setTransactionSuccessful() } finally { d.endTransaction() }
    }

    fun nextInvoiceNumber(): String = "F-${Calendar.getInstance().get(Calendar.YEAR)}-${String.format(Locale.US, "%04d", invoices().size + 1)}"
    fun clientForInvoice(invoiceId: Long): Client {
        val invoice = invoices().first { it.id == invoiceId }
        return readableDatabase.rawQuery("SELECT c.id,c.name,c.nif,c.email,c.street,c.street_number,c.postal_code,c.city,c.province FROM clients c JOIN invoices i ON c.id=i.client_id WHERE i.id=?", arrayOf(invoiceId.toString())).use {
            it.moveToFirst(); Client(it.getLong(0),it.getString(1),it.getString(2)?:"",it.getString(3)?:"",it.getString(4)?:"",it.getString(5)?:"",it.getString(6)?:"",it.getString(7)?:"",it.getString(8)?:"")
        }
    }
    fun invoices(): List<Invoice> { val out = mutableListOf<Invoice>(); readableDatabase.rawQuery("SELECT i.id,i.number,i.date,c.name,c.nif,i.irpf_rate,i.base,i.vat,i.irpf,i.total,i.status FROM invoices i JOIN clients c ON c.id=i.client_id ORDER BY i.id DESC", null).use { while (it.moveToNext()) out += Invoice(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4) ?: "", bd(it.getString(5)), bd(it.getString(6)), bd(it.getString(7)), bd(it.getString(8)), bd(it.getString(9)), it.getString(10) ?: "Pendiente") }; return out }
    fun lines(id: Long): List<Line> { val out = mutableListOf<Line>(); readableDatabase.rawQuery("SELECT id,invoice_id,description,qty,price,vat_rate,base,vat FROM lines WHERE invoice_id=? ORDER BY id", arrayOf(id.toString())).use { while (it.moveToNext()) out += Line(it.getLong(0), it.getLong(1), it.getString(2), bd(it.getString(3)), bd(it.getString(4)), bd(it.getString(5)), bd(it.getString(6)), bd(it.getString(7))) }; return out }
}
