package com.qsp.player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.util.ByteArrayBuffer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.AdapterView.OnItemClickListener;

public class QspGameStock extends TabActivity {

	public class GameItem {
		//Parsed
		String author;
		String ported_by;
		String version;
		String title;
		String lang;
		String player;
		String file_url;
		String desc_url;
		String pub_date;
		String mod_date;
		//Flags
		boolean downloaded;
		boolean checked;
		//Local
		String game_file;
		GameItem()
		{
			author = "";
			ported_by = "";
			version = "";
			title = "";
			lang = "";
			player = "";
			file_url = "";
			desc_url = "";
			pub_date = "";
			mod_date = "";
			downloaded = false;
			checked = false;
			game_file = "";
		}
	}
	
	final private Context uiContext = this;
	private String xmlGameListCached;
	private boolean openDefaultTab;	

    public static final int MAX_SPINNER = 1024;
	
	private String _zipFile; 
	private String _location; 
	
    String					startRootPath;
    String					backPath;
    ArrayList<File> 		qspGamesBrowseList;
	
	HashMap<String, GameItem> gamesMap;
	
	ListView lvAll;
	ListView lvDownloaded;
	ListView lvStarred;
    ProgressDialog downloadProgressDialog;
	
	

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        // Be sure to call the super class.
        super.onCreate(savedInstanceState);
        
        TabHost tabHost = getTabHost();
        LayoutInflater.from(getApplicationContext()).inflate(R.layout.gamestock, tabHost.getTabContentView(), true);
        tabHost.addTab(tabHost.newTabSpec("downloaded")
                .setIndicator("�����������")
                .setContent(R.id.downloaded_tab));
        tabHost.addTab(tabHost.newTabSpec("starred")
                .setIndicator("����������")
                .setContent(R.id.starred_tab));
        tabHost.addTab(tabHost.newTabSpec("all")
                .setIndicator("���")
                .setContent(R.id.all_tab));
        
    	gamesMap = new HashMap<String, GameItem>();
    	
    	openDefaultTab = true;
    	
    	InitListViews();
    	
    	setResult(RESULT_CANCELED);
        
        loadGameList.start();
        
        //TODO: 
        // 1. ����������� ������� "���������", �������� ������ ����.
        // 2. ����-���������� ���
        // 3. ����������� ������ ���
        // 4. ������ � �����, ���� ����� ������ ����������
        // 5. ����� ��� � ����� "�����������" � ������� ���������� ������� � ���
        // 6. ����������� ������� ���� �� ����� �����(����� ����������� ���� ���� ��������)
        // 7. ������ � ���������� ���������� ����� ���� ���� ��������
    }
    
    private void InitListViews()
    {
		lvAll = (ListView)findViewById(R.id.all_tab);
		lvDownloaded = (ListView)findViewById(R.id.downloaded_tab);
		lvStarred = (ListView)findViewById(R.id.starred_tab);

        lvDownloaded.setTextFilterEnabled(true);
        lvDownloaded.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvDownloaded.setOnItemClickListener(gameListClickListener);
		
        lvStarred.setTextFilterEnabled(true);
        lvStarred.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvStarred.setOnItemClickListener(gameListClickListener);

        lvAll.setTextFilterEnabled(true);
        lvAll.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvAll.setOnItemClickListener(gameListClickListener);
    }

    //������� ���� � ������
    private OnItemClickListener gameListClickListener = new OnItemClickListener() 
    {
    	@Override
    	public void onItemClick(AdapterView<?> parent, View arg1, int position, long arg3) 
    	{
    		String value = null;
    		switch (getTabHost().getCurrentTab()) {
    		case 0:
    			//�����������
    			value = lvDownloaded.getAdapter().getItem(position).toString();
    			break;
    		case 1:
    			//����������
    			value = lvStarred.getAdapter().getItem(position).toString();
    			break;
    		case 2:
    			//���
    			value = lvAll.getAdapter().getItem(position).toString();
    			break;
    		}
    		
    		GameItem selectedGame = gamesMap.get(value);
    		if (selectedGame == null)
    			return;
    		
    		if (selectedGame.downloaded)
    		{
    			//���� ���� ���������, ��������
    			Intent data = new Intent();
    			data.putExtra("file_name", selectedGame.game_file);
    			setResult(RESULT_OK, data);
    			finish();
    		}
    		else
    		{
    			//���� �� ���������, �������� ��������� � �������
    			DownloadGame(selectedGame.file_url, selectedGame.title);
    		}
    	}
    };
    
    private void DownloadGame(String file_url, String name)
    {
    	final String urlToDownload = file_url;
    	final String unzipLocation = Utility.GetDefaultPath().concat("/").concat(name).concat("/");
    	final String gameName = name;
    	downloadProgressDialog = new ProgressDialog(uiContext);
    	downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	downloadProgressDialog.setMax(MAX_SPINNER);
    	Thread t = new Thread() {
            public void run() {
            	try {
            		//set the download URL, a url that points to a file on the internet
            		//this is the file to be downloaded
            		URL url = new URL(urlToDownload);

            		//create the new connection
            		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            		//set up some things on the connection
            		urlConnection.setRequestMethod("GET");
            		urlConnection.setDoOutput(true);

            		//and connect!
            		urlConnection.connect();

            		//set the path where we want to save the file
            		//in this case, going to save it on the root directory of the
            		//sd card.
            		File SDCardRoot = Environment.getExternalStorageDirectory();
            		
            		File cacheDir = new File (SDCardRoot.getPath().concat("/Android/data/com.qsp.player/cache/"));
            		if (!cacheDir.exists())
            		{
            			if (!cacheDir.mkdirs())
            			{
            				Utility.WriteLog("Cannot create cache folder");
            				return;
            			}
            		}

            		//create a new file, specifying the path, and the filename
            		//which we want to save the file as.
            		String filename = String.valueOf(System.currentTimeMillis()).concat("_game.zip");
            		
            		File file = new File(cacheDir, filename);

            		//this will be used to write the downloaded data into the file we created
            		FileOutputStream fileOutput = new FileOutputStream(file);

            		//this will be used in reading the data from the internet
            		InputStream inputStream = urlConnection.getInputStream();

            		//create a buffer...
            		byte[] buffer = new byte[1024];
            		int bufferLength = 0; //used to store a temporary size of the buffer

            		//now, read through the input buffer and write the contents to the file
            		while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
            			//add the data in the buffer to the file in the file output stream (the file on the sd card
            			fileOutput.write(buffer, 0, bufferLength);
            			//this is where you would do something to report the prgress, like this maybe
            			updateSpinnerProgress(true, gameName, "�����������...", bufferLength);
            		}
            		//close the output stream when done
            		fileOutput.close();
            		
            		updateSpinnerProgress(false, "", "", 0);

            		//Unzip
            		Unzip(file.getPath(), unzipLocation, gameName);
            	 
            		updateSpinnerProgress(false, "", "", 0);

            		runOnUiThread(new Runnable() {
            			public void run() {
            				RefreshLists();
            			}
            		});

            	//catch some possible errors...
            	} catch (MalformedURLException e) {
            		e.printStackTrace();
            	} catch (IOException e) {
            		e.printStackTrace();
            	}
            }
        };
        
        t.start();
    }
    
    private void Unzip(String zipFile, String location, String gameName)
    {
    	_zipFile = zipFile;
    	_location = location;

    	updateSpinnerProgress(true, gameName, "���������������...", -1);

    	_dirChecker("");

    	try  { 
    		FileInputStream fin = new FileInputStream(_zipFile); 
    		ZipInputStream zin = new ZipInputStream(fin);
    		BufferedInputStream in = new BufferedInputStream(zin);

    		ZipEntry ze = null; 
    		while ((ze = zin.getNextEntry()) != null) { 
    			Log.v("Decompress", "Unzipping " + ze.getName()); 

    			if(ze.isDirectory()) { 
    				_dirChecker(ze.getName()); 
    			} else { 
    				FileOutputStream fout = new FileOutputStream(_location + ze.getName()); 
    				BufferedOutputStream out = new BufferedOutputStream(fout);
    				byte b[] = new byte[1024];
    				int n;
    				while ((n = in.read(b,0,1024)) >= 0) {
    					out.write(b,0,n);
    					updateSpinnerProgress(true, gameName, "���������������...", n);
    				}

    				zin.closeEntry();
    				out.close();
    				fout.close();
    			} 

    		} 
    		in.close();
    		zin.close(); 
    	} catch(Exception e) { 
    		Log.e("Decompress", "unzip", e); 
    	} 
    }
    
    private void _dirChecker(String dir) { 
    	File f = new File(_location + dir); 

    	if(!f.isDirectory()) { 
    		f.mkdirs(); 
    	} 
    }

    private void updateSpinnerProgress(boolean enabled, String title, String message, int nProgress)
    {
    	final boolean show = enabled;
    	final String dialogTitle = title;
    	final String dialogMessage = message;
    	final int nCount = nProgress;
		runOnUiThread(new Runnable() {
			public void run() {
				if (!show)
				{
					downloadProgressDialog.dismiss();
					return;
				}
				if (nCount>=0)
					downloadProgressDialog.incrementProgressBy(nCount);
				else
					downloadProgressDialog.setProgress(0);
				if (show && !downloadProgressDialog.isShowing())
				{
					downloadProgressDialog.setTitle(dialogTitle);
					downloadProgressDialog.setMessage(dialogMessage);
					downloadProgressDialog.show();
				}
			}
		});
    }

    private void RefreshLists()
    {
    	gamesMap.clear();
    	
		ParseGameList(xmlGameListCached);

		if (!ScanDownloadedGames())
			return;
		
		GetCheckedGames();
		
		
		RefreshAllTabs();
    }
    
    private boolean ScanDownloadedGames()
    {
    	//��������� ������ ��������� ���
    	
    	String path = Utility.GetDefaultPath();
    	if (path == null)
    		return false;
    	
        File gameStartDir = new File (path);
        File[] sdcardFiles = gameStartDir.listFiles();        
        ArrayList<File> qspGameDirs = new ArrayList<File>();
        ArrayList<File> qspGameFiles = new ArrayList<File>();
        //������� ��������� ��� �����
        for (File currentFile : sdcardFiles)
        {
        	if (currentFile.isDirectory() && !currentFile.isHidden() && !currentFile.getName().startsWith("."))
        	{
        		//�� ����� ��������� ������ ��, � ������� ���� ����
                File[] curDirFiles = currentFile.listFiles();        
                for (File innerFile : curDirFiles)
                {
                	if (!innerFile.isHidden() && (innerFile.getName().endsWith(".qsp") || innerFile.getName().endsWith(".gam")))
                	{
                		qspGameDirs.add(currentFile);
                		qspGameFiles.add(innerFile);
                		break;
                	}
                }
        	}
        }

        //���� ����������� ���� � �����
        for (int i=0; i<qspGameDirs.size(); i++)
        {
        	File d = qspGameDirs.get(i);
        	String displayName = d.getName();
        	GameItem game = gamesMap.get(displayName);
        	if (game == null)
        	{
        		game = new GameItem();
        		game.title = displayName;
        	}
        	File f = qspGameFiles.get(i);
    		game.game_file = f.getPath();
    		game.downloaded = true;
    		gamesMap.put(displayName, game);
        }
    
        return true;
    }
    
    private void GetCheckedGames()
    {
    	//!!! STUB
    	//��������� ������ ���������� ���
    	
    }
   
    private void RefreshAllTabs()
    {
    	//������� ������ ��� �� �����

    	//���
        int gamesCount = gamesMap.size();
        final String []gamesAll = new String[gamesCount];
        int i = 0;
        for (HashMap.Entry<String, GameItem> e : gamesMap.entrySet())
        {
        	gamesAll[i] = e.getKey();
        	i++;
        }
        lvAll.setAdapter(new ArrayAdapter<String>(uiContext, R.layout.act_item, gamesAll));

        //�����������
		Vector<String> gamesDownloaded = new Vector<String>();
        for (HashMap.Entry<String, GameItem> e : gamesMap.entrySet())
        {
        	if (e.getValue().downloaded)
        		gamesDownloaded.add(e.getKey());
        }
        String []gamesD = gamesDownloaded.toArray(new String[gamesDownloaded.size()]);
        lvDownloaded.setAdapter(new ArrayAdapter<String>(uiContext, R.layout.act_item, gamesD));
        
        //����������
        //!!! STUB
        String []gamesStarred = new String[0];
        lvStarred.setAdapter(new ArrayAdapter<String>(uiContext, R.layout.act_item, gamesStarred));
        
        //����������, ����� ������� �������
        if (openDefaultTab)
        {
        	openDefaultTab = false;
        	
        	int tabIndex = 0;//�����������
        	if (lvDownloaded.getAdapter().isEmpty())
        	{
        		if (lvStarred.getAdapter().isEmpty())
        			tabIndex = 2;//���
        		else
        			tabIndex = 1;//����������
        	}
        	
        	getTabHost().setCurrentTab(tabIndex);
        }
    }
    
    private boolean ParseGameList(String xml)
    {
    	boolean parsed = false;
    	GameItem curItem = null;
    	try {
    		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    		factory.setNamespaceAware(true);
    		XmlPullParser xpp = factory.newPullParser();

    		xpp.setInput( new StringReader ( xml ) );
    		int eventType = xpp.getEventType();
    		boolean doc_started = false;
    		boolean list_started = false;
    		String lastTagName = "";
    		while (eventType != XmlPullParser.END_DOCUMENT) {
    			if(eventType == XmlPullParser.START_DOCUMENT) {
    				doc_started = true;
    			} else if(eventType == XmlPullParser.END_DOCUMENT) {
    				//Never happens
    			} else if(eventType == XmlPullParser.START_TAG) {
    				if (doc_started)
    				{
    					lastTagName = xpp.getName();
    					if (lastTagName.equals("game_list"))
    					{
    						list_started = true;
    					}
    					if (list_started)
    					{
    						if (lastTagName.equals("game"))
    							curItem = new GameItem();
    					}            		 
    				}
    			} else if(eventType == XmlPullParser.END_TAG) {
    				if (doc_started && list_started)
    				{
    					if (xpp.getName().equals("game"))
    					{
    						gamesMap.put(curItem.title, curItem);
    					}
    					if (xpp.getName().equals("game_list"))
    						parsed = true;
    					lastTagName = "";
    				}
    			} else if(eventType == XmlPullParser.CDSECT) {
    				if (doc_started && list_started)
    				{
    					String val = xpp.getText();
    					if (lastTagName.equals("author"))
    						curItem.author = val;
    					else if (lastTagName.equals("ported_by"))
    						curItem.ported_by = val;
    					else if (lastTagName.equals("version"))
    						curItem.version = val;
    					else if (lastTagName.equals("title"))
    						curItem.title = val;
    					else if (lastTagName.equals("lang"))
    						curItem.lang = val;
    					else if (lastTagName.equals("player"))
    						curItem.player = val;
    					else if (lastTagName.equals("file_url"))
    						curItem.file_url = val;
    					else if (lastTagName.equals("desc_url"))
    						curItem.desc_url = val;
    					else if (lastTagName.equals("pub_date"))
    						curItem.pub_date = val;
    					else if (lastTagName.equals("mod_date"))
    						curItem.mod_date = val;
    				}
    			}
   				eventType = xpp.nextToken();
    		}
    	} catch (XmlPullParserException e) {
    		String errTxt = "Exception occured while trying to parse game list, XML corrupted at line ".
    					concat(String.valueOf(e.getLineNumber())).concat(", column ").
    					concat(String.valueOf(e.getColumnNumber())).concat(".");
    		Utility.WriteLog(errTxt);
    	} catch (Exception e) {
    		Utility.WriteLog("Exception occured while trying to parse game list, unknown error");
    	}
    	if (!parsed)
    	{
    		//���������� ��������� �� ������
    		new AlertDialog.Builder(uiContext)
    		.setMessage("������: �� ������� ��������� ������ ���. ��������� ��������-�����������.")
    		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int whichButton) { }
    		})
    		.show();
    	}
    	return parsed;
    }

    private Thread loadGameList = new Thread() {
    	//��������� ������ ���
        public void run() {
            try {
                URL updateURL = new URL("http://qsp.su/gamestock/games-ru.xml");
                URLConnection conn = updateURL.openConnection();
                InputStream is = conn.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);
                ByteArrayBuffer baf = new ByteArrayBuffer(50);

                int current = 0;
                while((current = bis.read()) != -1){
                    baf.append((byte)current);
                }

                /* Convert the Bytes read to a String. */
                final String xml = new String(baf.toByteArray());
    			runOnUiThread(new Runnable() {
    				public void run() {
    					xmlGameListCached = xml;
   						RefreshLists();
    				}
    			});
            } catch (Exception e) {
            	Utility.WriteLog("Exception occured while trying to load game list");
    			runOnUiThread(new Runnable() {
    				public void run() {
    					RefreshLists();
    				}
    			});
            }
        }
    };
    
    
    //***********************************************************************
    //			����� ����� "��������", ����� ������������� �����
    //***********************************************************************
    android.content.DialogInterface.OnClickListener browseFileClick = new DialogInterface.OnClickListener()
    {
    	//�������� UI
		@Override
		public void onClick(DialogInterface dialog, int which) 
		{
			boolean canGoUp = !backPath.equals("");
			int shift = 0;
			if (canGoUp)
				shift = 1;
			if (which == 0 && canGoUp)
			{
				dialog.dismiss();
				BrowseGame(backPath, false);
			}
			else
			{
				File f = qspGamesBrowseList.get(which - shift);
				if (f.isDirectory())
					BrowseGame(f.getPath(), false);
				else
				{
					//������� ���������� ������� �� ��������
					//!!! STUB
					//runGame(f.getPath());
				}
			}
		}    	
    };
    
    private void BrowseGame(String startpath, boolean start)
    {
    	//�������� UI
    	if (startpath == null)
    		return;
    	
    	//������������� ���� "����"    	
    	if (!start)
    		if (startRootPath.equals(startpath))
    			start = true;
    	if (!start)
    	{
    		int slash = startpath.lastIndexOf(File.separator, startpath.length() - 2);
    		if (slash >= 0)
    			backPath = startpath.substring(0, slash + 1);
    		else
    			start = true;
    	}
    	if (start)
    	{
    		startRootPath = startpath;
    		backPath = "";
    	}
    	
        //���� ��� ����� .qsp � .gam � ����� ������
        File sdcardRoot = new File (startpath);
        File[] sdcardFiles = sdcardRoot.listFiles();        
        qspGamesBrowseList = new ArrayList<File>();
        //������� ��������� ��� �����
        for (File currentFile : sdcardFiles)
        {
        	if (currentFile.isDirectory() && !currentFile.isHidden() && !currentFile.getName().startsWith("."))
        		qspGamesBrowseList.add(currentFile);
        }
        //����� ��������� ��� QSP-����
        for (File currentFile : sdcardFiles)
        {
        	if (!currentFile.isHidden() && (currentFile.getName().endsWith(".qsp") || currentFile.getName().endsWith(".gam")))
        		qspGamesBrowseList.add(currentFile);
        }
        
        //���� �� �� �� ����� ������� ������, �� ��������� ������ 
        int shift = 0;
        if (!start)
        	shift = 1;
        int total = qspGamesBrowseList.size() + shift;
        final CharSequence[] items = new String[total];
        if (!start)
            items[0] = "[..]";
        for (int i=shift; i<total; i++)
        {
        	File f = qspGamesBrowseList.get(i - shift);
        	String displayName = f.getName();
        	if (f.isDirectory())
        		displayName = "["+ displayName + "]";
        	items[i] = displayName;
        }
        
        //���������� ������ ������ �����
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("�������� ���� � �����");
        builder.setItems(items, browseFileClick);
        AlertDialog alert = builder.create();
        alert.show();
    }
    
}
