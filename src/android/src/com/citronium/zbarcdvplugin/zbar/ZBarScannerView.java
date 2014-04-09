package com.citronium.zbarcdvplugin.zbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.citronium.zbarcdvplugin.core.BarcodeScannerView;
import com.citronium.zbarcdvplugin.core.DisplayUtils;

public class ZBarScannerView extends BarcodeScannerView {
    public interface ResultHandler {
        public void handleResult(Result rawResult);
    }

    static {
        System.loadLibrary("iconv");
    }

    private ImageScanner mScanner;
    private List<BarcodeFormat> mFormats;
    private ResultHandler mResultHandler;

    public ZBarScannerView(Context context) {
        super(context);
        setupScanner();
    }

    public ZBarScannerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setupScanner();
    }

    public void setFormats(List<BarcodeFormat> formats) {
        mFormats = formats;
        setupScanner();
    }

    public void setResultHandler(ResultHandler resultHandler) {
        mResultHandler = resultHandler;
    }

    public Collection<BarcodeFormat> getFormats() {
        if(mFormats == null) {
            return BarcodeFormat.ALL_FORMATS;
        }
        return mFormats;
    }

    public void setupScanner() {
        mScanner = new ImageScanner();
        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);

        mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
        for(BarcodeFormat format : getFormats()) {
            mScanner.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        boolean isPortrait = (DisplayUtils.getScreenOrientation(getContext()) == Configuration.ORIENTATION_PORTRAIT);

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();

        int width = size.width;
        int height = size.height;
        Rect frame = (isPortrait) ? getFramingRectInPreview(height, width) : getFramingRectInPreview(width, height);

        /*String crp_cag = "crop";
        Log.d(crp_cag, "Orientation isPortrait: " + isPortrait);
        Log.d(crp_cag, "Camera preview size: " + width + "x" + height);
        Point screenResolution = DisplayUtils.getScreenResolution(getContext());
        Log.d(crp_cag, "Screen preview size: " + screenResolution.x + "x" + screenResolution.y);
        Log.d(crp_cag, "New frame coords: " + "top: " + frame.top + ", " + "left: " + frame.left + ", " + "width: " + frame.width() + ", " + "height: " + frame.height());
        */

        if (isPortrait) {
            byte[] rotatedData = new byte[data.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++)
                    rotatedData[x * height + height - y - 1] = data[x + y * width];
            }
            int tmp = width;
            width = height;
            height = tmp;
            data = rotatedData;
        }

        Image barcode = new Image(width, height, "Y800");
        barcode.setData(data);
        barcode.setCrop(frame.left, frame.top, frame.width(), frame.height());
        //see coords here http://stackoverflow.com/questions/18918211/how-to-change-area-of-scan-zbar

        int result = mScanner.scanImage(barcode);

        if (result != 0) {
            stopCamera();
            if(mResultHandler != null) {
                SymbolSet syms = mScanner.getResults();
                Result rawResult = new Result();
                for (Symbol sym : syms) {
                    String symData = sym.getData();
                    if (!TextUtils.isEmpty(symData)) {
                        rawResult.setContents(symData);
                        rawResult.setBarcodeFormat(BarcodeFormat.getFormatById(sym.getType()));
                        break;
                    }
                }
                mResultHandler.handleResult(rawResult);
            }
        } else {
            camera.setOneShotPreviewCallback(this);
        }
    }
}
