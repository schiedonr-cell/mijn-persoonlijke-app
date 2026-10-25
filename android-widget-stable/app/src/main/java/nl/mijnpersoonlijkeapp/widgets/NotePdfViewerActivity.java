package nl.mijnpersoonlijkeapp.widgets;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Alleen binnen de bestaande Mijn dag-app: direct PDF's bekijken zonder Android-appkiezer. */
public final class NotePdfViewerActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ImageView pageImage;
    private TextView pageLabel;
    private Button previousButton;
    private Button nextButton;
    private HorizontalScrollView horizontal;
    private ScrollView vertical;
    private Bitmap currentBitmap;
    private int pageIndex=0, pageCount=0, sequence=0;
    private float zoom=1f;
    private volatile boolean closing=false;

    private int dp(float value){
        return (int)(getResources().getDisplayMetrics().density*value+.5f);
    }
    private Button control(String label){
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff26362e));
        button.setMinHeight(dp(48));
        return button;
    }
    private LinearLayout.LayoutParams weighted(){
        return new LinearLayout.LayoutParams(0,dp(52),1);
    }
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff161a17);
        getWindow().setNavigationBarColor(0xff161a17);
        final String name=getIntent().getStringExtra("name");
        final String path=getIntent().getStringExtra("path");
        try{
            if(path==null)throw new IOException("Geen PDF");
            File parent=new File(getCacheDir(),"note-files").getCanonicalFile();
            File pdf=new File(path).getCanonicalFile();
            if(!pdf.getParentFile().equals(parent)||!pdf.isFile()||pdf.length()>8*1024*1024)
                throw new IOException("Ongeldige PDF");
            descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);
            renderer=new PdfRenderer(descriptor);
            pageCount=renderer.getPageCount();
            if(pageCount<1)throw new IOException("Lege PDF");
        }catch(Exception error){
            Toast.makeText(this,"Dit PDF-document kan niet worden weergegeven.",Toast.LENGTH_LONG).show();
            finish();return;
        }

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff191d1a);
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8),dp(3),dp(12),dp(3));
        Button back=control("‹ Terug");
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(112),dp(52)));
        TextView title=new TextView(this);
        title.setText(name==null||name.trim().isEmpty()?"PDF bekijken":name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,dp(52),1);
        titleParams.leftMargin=dp(8);
        header.addView(title,titleParams);
        root.addView(header);

        horizontal=new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        horizontal.setBackgroundColor(0xff303532);
        vertical=new ScrollView(this);
        vertical.setFillViewport(true);
        vertical.setBackgroundColor(0xff303532);
        pageImage=new ImageView(this);
        pageImage.setScaleType(ImageView.ScaleType.FIT_XY);
        pageImage.setContentDescription("PDF-pagina");
        vertical.addView(pageImage,new ScrollView.LayoutParams(dp(300),dp(400)));
        horizontal.addView(vertical,new HorizontalScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(horizontal,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setPadding(dp(4),dp(4),dp(4),dp(8));
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        previousButton=control("‹");
        nextButton=control("›");
        Button minus=control("−");
        Button plus=control("+");
        pageLabel=new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setTextSize(15);
        pageLabel.setGravity(Gravity.CENTER);
        bottom.addView(previousButton,weighted());
        bottom.addView(minus,weighted());
        bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(52),2));
        bottom.addView(plus,weighted());
        bottom.addView(nextButton,weighted());
        previousButton.setOnClickListener(v->{if(pageIndex>0){pageIndex--;renderPage();}});
        nextButton.setOnClickListener(v->{if(pageIndex<pageCount-1){pageIndex++;renderPage();}});
        minus.setOnClickListener(v->{if(zoom>1f){zoom=Math.max(1f,zoom-.5f);renderPage();}});
        plus.setOnClickListener(v->{if(zoom<2.5f){zoom=Math.min(2.5f,zoom+.5f);renderPage();}});
        root.addView(bottom,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(65)));
        renderPage();
    }

    private void renderPage(){
        if(renderer==null||closing)return;
        final int requestedPage=pageIndex;
        final float requestedZoom=zoom;
        final int request=++sequence;
        pageLabel.setText((pageIndex+1)+" / "+pageCount);
        previousButton.setEnabled(pageIndex>0);
        nextButton.setEnabled(pageIndex<pageCount-1);
        pageImage.setContentDescription("Pagina "+(pageIndex+1)+" van "+pageCount);
        worker.execute(()->{
            Bitmap output=null;
            PdfRenderer.Page page=null;
            try{
                if(closing)return;
                page=renderer.openPage(requestedPage);
                float w=page.getWidth(),h=page.getHeight();
                float screenWidth=Math.max(280,getResources().getDisplayMetrics().widthPixels-dp(18));
                int width=Math.min(2200,Math.round(screenWidth*requestedZoom));
                int height=Math.min(3200,Math.max(1,Math.round(width*h/w)));
                output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                output.eraseColor(Color.WHITE);
                page.render(output,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap completed=output;
                runOnUiThread(()->{
                    if(closing||request!=sequence){completed.recycle();return;}
                    Bitmap old=currentBitmap;
                    currentBitmap=completed;
                    ViewGroup.LayoutParams lp=pageImage.getLayoutParams();
                    lp.width=completed.getWidth();lp.height=completed.getHeight();
                    pageImage.setLayoutParams(lp);
                    pageImage.setImageBitmap(completed);
                    horizontal.post(()->horizontal.scrollTo(0,0));
                    vertical.post(()->vertical.scrollTo(0,0));
                    if(old!=null && !old.isRecycled())old.recycle();
                });
            }catch(Exception ex){
                if(output!=null)output.recycle();
                runOnUiThread(()->{
                    if(!closing&&request==sequence)Toast.makeText(this,
                        "Deze PDF-pagina kan niet worden weergegeven.",Toast.LENGTH_LONG).show();
                });
            }finally{
                if(page!=null)page.close();
            }
        });
    }

    @Override protected void onDestroy(){
        closing=true;
        sequence++;
        worker.execute(()->{
            try{if(renderer!=null)renderer.close();}catch(Exception ignored){}
            try{if(descriptor!=null)descriptor.close();}catch(Exception ignored){}
        });
        worker.shutdown();
        super.onDestroy();
    }
}
