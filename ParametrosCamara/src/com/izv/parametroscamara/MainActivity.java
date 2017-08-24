package com.izv.parametroscamara;

import java.io.File;
import java.io.FileOutputStream;
import java.util.GregorianCalendar;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SlidingDrawer;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	
	int RESULTADO_GALERIA=1, RESULTADO_CAMARA=2;
	
	private SharedPreferences sp;

	private RadioButton rbJpg, rbPng;
	private Switch swAlmacenamiento;
	private ToggleButton tbNombre;
	private EditText etNombre;
	private ProgressBar pbLeyendo;
	private Button btCamara, btGaleria;
	
	private String formato, almacenamiento;
	private boolean poneNombreDefecto;
	
	//Está deprecated desde la API 17
	private SlidingDrawer slidingDrawer;
	
	private boolean panelParametrosMostrado=false;
	
	private HiloFoto hilo;
	private ContentValues values;
	private Uri imagenUri;
	
	//Por el tema del hilo
	private Manejador maneja;
	private Thread th;
	
	private String nombreArchivo;
	
	private boolean botonAtras=true;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//Si el dispositivo no tiene camara no se hara nada
		if(presenciaCamara()){			
			inicio();			
		} else{
			Toast.makeText(this, R.string.error_no_camara, Toast.LENGTH_SHORT).show();
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
    protected void onSaveInstanceState(Bundle savingInstanceState) {
    	
    	super.onSaveInstanceState(savingInstanceState);
    	
    	//Si el panel esta abierto guardara true y si no false
    	panelParametrosMostrado=slidingDrawer.isOpened();
    	savingInstanceState.putBoolean("panelMostrado", panelParametrosMostrado);    	
    	
    }
   
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	
    	super.onRestoreInstanceState(savedInstanceState);

    	panelParametrosMostrado=savedInstanceState.getBoolean("panelMostrado");
    	
    	//Si el panel estaba como true se abrira, si no nada y en este caso desaparece
    	if(panelParametrosMostrado){
    		slidingDrawer.animateOpen();
    	}
    	
    }
	
	public void inicio(){
		
		pbLeyendo=(ProgressBar)findViewById(R.id.pbCargando);
		pbLeyendo.setVisibility(View.GONE);
		
		btCamara=(Button)findViewById(R.id.btCamara);
		btGaleria=(Button)findViewById(R.id.btGaleria);
				
		//Se utiliza para subir el panel solo
		maneja=new Manejador();
		
		//Si no existe la carpeta publica externa DCIM la crea
		existePublicaExterna();

		//Se asignan todas las variables a sus componentes
		slidingDrawer=(SlidingDrawer)findViewById(R.id.slidingDrawer);
		
		rbJpg=(RadioButton)findViewById(R.id.rbJpg);
		rbPng=(RadioButton)findViewById(R.id.rbPng);
		
		swAlmacenamiento=(Switch)findViewById(R.id.swAlmacenamiento);
		tbNombre=(ToggleButton)findViewById(R.id.tbNombre);
		etNombre=(EditText)findViewById(R.id.etNombre);
		
		//Se utiliza este metodo para acceder o crear las preferencias compartidas
		sp=getSharedPreferences("preferencias", Context.MODE_PRIVATE);		

		//Se guardan en las variables de la clase y se modifican los botones que correspondan
		leerSp();
		
		//Escuchador cada vez que se introduzca algo en el campo texto, asi guarda automaticamente
		//las preferencias
		etNombre.addTextChangedListener(new TextWatcher(){
	        public void afterTextChanged(Editable s) {
	            escribirSp(null);
	        }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
	        public void onTextChanged(CharSequence s, int start, int before, int count){}
	    }); 
		
		//Se inicia el hilo
		iniciaHilo();
						
	}
	
	public void escribirSp(View v){
		
		//Se crea un objeto editor y se comprueban todos los estado con isChecked
		Editor ed=sp.edit();
		
		if(rbJpg.isChecked()){
			formato="jpg";
		} else{
			formato="png";
		}
		
		if(swAlmacenamiento.isChecked()){
			almacenamiento="privada";
		} else{
			almacenamiento="externa";
		}
		
		poneNombreDefecto=tbNombre.isChecked();
		
		//Se colocan los datos en el editor
		ed.putString("formato", formato);
		ed.putString("almacenamiento", almacenamiento);
		ed.putBoolean("nombreDefecto", poneNombreDefecto);
		ed.putString("nombre", etNombre.getText().toString());
		
		ed.commit();
		
		//Si esta marcada la opcion de escribir el nombre aparece su campo
		mostrarEtNombre();
		
	}
	
	public void leerSp(){
		
		//Se recoge la informacion de las preferencias compartidas
		formato=sp.getString("formato", "jpg");
		almacenamiento=sp.getString("almacenamiento", "privada");
		poneNombreDefecto=sp.getBoolean("nombreDefecto", true);
		etNombre.setText(sp.getString("nombre", ""));
		
		if(formato.equals("jpg")){
			rbJpg.setChecked(true);
		} else{
			rbPng.setChecked(true);
		}
		
		if(almacenamiento.equals("privada")){
			swAlmacenamiento.setChecked(true);
		} else{
			swAlmacenamiento.setChecked(false);
		}
		
		mostrarEtNombre();
				
	}
	
	//Pone visible o no el editText de introducir el nombre del archivo a mano
	public void mostrarEtNombre(){
		
		if(poneNombreDefecto){
			tbNombre.setChecked(true);
			etNombre.setVisibility(View.GONE);
		} else{
			tbNombre.setChecked(false);
			etNombre.setVisibility(View.VISIBLE);
			etNombre.requestFocus();
		}
				
	}
	
	//Se comprueba si existe la cámara en el dispositivo
	public boolean presenciaCamara(){
		
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			return true;
		} else {
			return false;
		}
		
	}
	
	//Se comprueba la disponibilidad del intent
	/*public static boolean intentDisponible(Context contexto, String accion) {
		
		final PackageManager adminPaquetes = contexto.getPackageManager();
		final Intent intent = new Intent(accion);
		
		List<ResolveInfo> lista = adminPaquetes.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);
			
		return lista.size() > 0;
			
	}*/
	
	//Boton para echar una foto
	public void camara(View v){
		
		if(!etNombre.getText().toString().isEmpty() || poneNombreDefecto){
		
			//Llama al intent de la camara
			values = new ContentValues();
			values.put(MediaStore.Images.Media.TITLE, "New Picture");
			values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
			
			imagenUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
			
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, imagenUri);
			startActivityForResult(intent, RESULTADO_CAMARA);
			
		} else{
			Toast.makeText(this, R.string.error_no_nombre, Toast.LENGTH_SHORT).show();
		}
		
		
		
	}
	
	//Boton para iniciar la galeria
	public void galeria(View v){
		
		//Se inicia la galeria esperando su respuesta
		Intent intent = new Intent(Intent.ACTION_PICK);
		intent.setType("image/*");
		startActivityForResult(intent, RESULTADO_GALERIA);
				
	}
	
	//Recoger los datos del intent
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if(requestCode==RESULTADO_GALERIA && resultCode==Activity.RESULT_OK){
			
			//Se obtiene la uri para visualizar la imagen con la aplicacion de la galeria
			Uri uri=data.getData();
			
			//Se indica que se va a abrir un archivo
			Intent intent = new Intent(Intent.ACTION_VIEW);
			//Se dice el archivo que tiene que abrir y el tipo de archivo que es
			intent.setDataAndType(uri, "image/*");
			//Abre el visor de imagenes del dispositivo
			startActivity(intent);
			
		} else{
			
			if(requestCode==RESULTADO_CAMARA && resultCode==Activity.RESULT_OK){
				
				//tv.setText("Guardando imagen... por favor, no salir de la aplicación");
				//btFoto.setEnabled(false);
				hilo = new HiloFoto(this);
				hilo.execute();	
								
			}
			
		}
		
	}
	
	//Se le pasa un string que es el nombre del archivo. Si no existe el archivo lo devolvera tal cual esta.
	//Si ya existe pondra _numero al final, segun el numero que corresponda
	public String getNombreArchivo(String nombre){

		File archivo;
		
		//Se crea la ruta completa del archivo
		if(almacenamiento.equals("privada")){
			archivo = new File(getExternalFilesDir(Environment.DIRECTORY_DCIM)+"/"+nombre);
		} else{
			archivo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/"+nombre);
		}
	
		//Si el archivo no existe devuelve el nombre como ha entrado, sin hacer modificaciones
		if(!archivo.exists()){		
			return nombre;			
		} else{
			
			//Se guarda solo el nombre del archivo
			String nombreArchivo=archivo.getName();
			
			//Se obtiene la posicion de la barra para saber si solo existe un archivo con el mismo nombre o si es el usuario
			//el que ha introducido una barra baja en el nombre 
			//Se quiere coger la ultima barra baja por si ha sido el usuario el que la ha escrito
			int posBarra=nombreArchivo.lastIndexOf("_");
			
			//Si es -1 significa que no hay barra baja y por tanto solo hay un archivo con ese nombre
			if(posBarra==-1){
				//Se crea el nuevo nombre.
				//Coge todo el nombre menos los 4 ultimos caracteres que corresponderan con ".jpg" o ".png"
				//y le añade el "_1", el punto y su extension
				//Se vuelve a llamar a esta misma funcion, ya se sabe que no existe un nombre igual que el creado...
				return getNombreArchivo(nombreArchivo.substring(0, nombreArchivo.length()-4)+"_1."+formato);
				
			//Si entra aqui significa que o hay dos o mas nombres iguales diferenciados por "_numero" o el usuario ha metido
			// barra baja en el nombre de su imagen. Es decir, el nombre del archivo tiene alguna barra baja.
			} else{
				
				//Se obtiene la posicion del punto para saber si lo que hay entre la barra baja y el punto es un numero.
				//Solo interesa la ultima posicion donde aparezca el punto, que es el que determina la extension
				int posPunto=nombreArchivo.lastIndexOf(".");
				
				//Se guarda en este string lo que habia entre "_" y ".". Puede haber un numero, eso seria que 
				//ya existe algun nombre repetido o que haya letras y numeros, quiere decir que el usuario ha metido
				// alguna "_" en el nombre de su archivo
				String posibleNumeroString=nombreArchivo.substring(posBarra+1, posPunto);
		
				//Se hace el try catch para captura el posible error
				try{
					
					//Al convertirlo a int si hay alguna letra o caracter dara NullPointerException
					int posibleNumero=Integer.parseInt(posibleNumeroString);
										
					//Si sigue por aqui significa que era numero, por tanto se vuelve a armar el nombre del archivo
					//pero sumandole 1.
					//Primero se coge el nombre normal, desde el principio hasta la barra y se pone posBarra+1 para que
					//tambien coja la barra
					//Como ya tenemos en una variable posibleNumero solo hay que sumarle 1
					//Para finalizar se le pone el "." y su formato
					nombreArchivo=nombreArchivo.substring(0, posBarra+1)+(posibleNumero+1)+"."+formato;
					
					//Se vuelve a llamar a este metodo para que compruebe si al sumar 1 es ya el ultimo archivo con el mismo
					//nombre o tiene que sumar mas
					//Cuando sume lo suficiente se parara en el primer if del metodo y devolvera su nombre correcto
					return getNombreArchivo(nombreArchivo);
					
				} catch(NumberFormatException error){					
			
					//Si ha dado error significa que ha sido el usuario el que ha puesto una barra baja, pero como
					//la ultima no era numero significa que solo habia un nombre igual, por tanto se vuelve a coger
					//por partes el archivo y se le incorpora "_1".
					//La proxima vez que entre lo que habra entre la barra baja y el punto sera un numero, por tanto
					//ya no entrara aqui
					return getNombreArchivo(nombreArchivo.substring(0, nombreArchivo.length()-4)+"_1."+formato);					
				}
				
			}
								
		}
				
	}
	
	public void existePublicaExterna(){
		
		//Creamos un archivo con la ruta de la carpeta publica externa de las fotos y si no existe la crea
		File f=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
		
		if(!f.exists()){
			f.mkdir();
		}
		
	}
	
	
	
//////FORMA UN POCO RARA DE ECHAR A ANDAR EL HILO/////

	public void iniciaHilo(){
		
		th=new Thread(new HiloManejador());
		th.start();
		
	}
	
	private class Manejador extends Handler{
		
		@Override
		public void handleMessage(Message msg){
			if(!slidingDrawer.isOpened()){
				slidingDrawer.animateToggle();
	    	}
			
		}
		
	}
	
	private class HiloManejador implements Runnable{

		@Override
		public void run() {
			
			try {
				Thread.sleep(6000);
			} catch (InterruptedException e) {
			}
			
			Message msg =new Message();
			maneja.sendMessage(msg);
			
		}
		
	}	


	
	
	
	//Compresion de la imagen en un hilo
    private class HiloFoto extends AsyncTask<Void, Void, Boolean>{

    	private Context ct;
    	    	
    	public HiloFoto(Context ctx){
    		this.ct=ctx;
    		
    	}
    	
		@Override
		protected Boolean doInBackground(Void... params) {

			Bitmap thumbnail;

			//Si no existe camara en el dispositivo no se hará la foto
			if(presenciaCamara()){
							
				//Si esta el togglebuton activo se crea el nombre del archivo
				if(poneNombreDefecto){
							
					//Se crea una objeto GregorianCalendar que da la hora actual
					GregorianCalendar fecha=new GregorianCalendar();
							
					//Se saca el mes y en caso de tener un digito se le pone un 0 delante
					String mes=(fecha.get(GregorianCalendar.MONTH)+1)+"";
					if(mes.length()==1){
						mes="0"+mes;
					}
				
					//Se saca el dia y en caso de tener un digito se le pone un 0 delante
					String dia=fecha.get(GregorianCalendar.DAY_OF_MONTH)+"";
					if(dia.length()==1){
						dia="0"+dia;
					}
				
					//Se saca la hora y en caso de tener un digito se le pone un 0 delante
					String hora=fecha.get(GregorianCalendar.HOUR_OF_DAY)+"";
					if(hora.length()==1){
						hora="0"+hora;
					}
				
					//Se saca el minuto y en caso de tener un digito se le pone un 0 delante
					String minutos=fecha.get(GregorianCalendar.MINUTE)+"";
					if(minutos.length()==1){
						minutos="0"+minutos;
					}
				
					//Se sacan los segundos y en caso de tener un digito se le pone un 0 delante
					String segundos=fecha.get(GregorianCalendar.SECOND)+"";
					if(segundos.length()==1){
						segundos="0"+segundos;
					}
				
					//Se une el nombre del fichero
					nombreArchivo="IMG_"+fecha.get(GregorianCalendar.YEAR)+mes+dia+"_"+hora+minutos+segundos+"."+formato;
							
				//Si se elige el nombre del fichero hace esto
				} else{
				
					//Se obtiene el string
					nombreArchivo=etNombre.getText().toString();
				
					//Se comprueba que no este vacio
					if(!nombreArchivo.isEmpty()){
					
						//Se llama al metodo que se asegura que no exista un nombre igual
						nombreArchivo=getNombreArchivo(nombreArchivo+"."+formato);
					
					}else{
						Toast.makeText(MainActivity.this, R.string.error_no_nombre, Toast.LENGTH_SHORT).show();
					}
				
				}
			
			} else{
				Toast.makeText(MainActivity.this, R.string.error_no_camara, Toast.LENGTH_SHORT).show();
			}
			
			// recoger foto temporal, comprimirla y eliminarla
            try {
                thumbnail = MediaStore.Images.Media.getBitmap(getContentResolver(), imagenUri);
                
                File archivo;
                
              //Se indica donde se guardara
				if(almacenamiento.equals("privada")){				
					archivo = new File(getExternalFilesDir(Environment.DIRECTORY_DCIM)+"/"+nombreArchivo);
				} else{
					archivo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/"+nombreArchivo);
				}
                
				Log.v("RUTA", imagenUri.getPath());
				
                FileOutputStream out = new FileOutputStream(archivo);
                
                if(formato.equals("jpg")){
                	thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, out);
                }else{
                	thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                }
                
                thumbnail.recycle(); //liberamos la imagen
                return true;
                
            } catch (Exception e) {
            	Toast.makeText(MainActivity.this, R.string.error_imagen_grande, Toast.LENGTH_SHORT).show();
            	return false;
            }
            
		}
		
		@Override
		protected void onPreExecute() {
			
			pbLeyendo.setVisibility(View.VISIBLE);
			
			if(slidingDrawer.isOpened()){
				slidingDrawer.animateOpen();
	    	}
			
			btCamara.setEnabled(false);
			btGaleria.setEnabled(false);
			
			botonAtras=false;
			
		}
		
		@Override
    	protected void onPostExecute(Boolean result)  {
    		
			pbLeyendo.setVisibility(View.GONE);
			btCamara.setEnabled(true);
			btGaleria.setEnabled(true);
			
			botonAtras=true;
    		
    	} 

    }
    

	//Sobreescribe la accion de los botones
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		
		if (botonAtras){
			return super.onKeyDown(keyCode, event);
		} else{
			return false;
		}
	
	}
	
}
	
		