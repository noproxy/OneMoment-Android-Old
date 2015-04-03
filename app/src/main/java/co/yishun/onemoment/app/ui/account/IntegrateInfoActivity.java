package co.yishun.onemoment.app.ui.account;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import co.yishun.onemoment.app.R;
import co.yishun.onemoment.app.config.ErrorCode;
import co.yishun.onemoment.app.net.request.account.IdentityInfo;
import co.yishun.onemoment.app.ui.ToolbarBaseActivity;
import co.yishun.onemoment.app.util.AccountHelper;
import co.yishun.onemoment.app.util.LogUtil;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import org.androidannotations.annotations.*;
import org.androidannotations.annotations.res.StringArrayRes;

/**
 * Created by Carlos on 2015/4/2.
 */
@EActivity(R.layout.activity_integrate_info)
public class IntegrateInfoActivity extends ToolbarBaseActivity {
    public static final String TAG = LogUtil.makeTag(IntegrateInfoActivity.class);
    public static final int REQUEST_PHONE = 0;
    public static final int REQUEST_WEIBO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
    }

    //    public static final String EXTRA_SIGN_UP_TYPE = "type";
//    public static final String EXTRA_PHONE = "phone";

//    public enum SignUpType {phone, weibo}

    @ViewById
    EditText nickNameEditText;

    @StringArrayRes
    String[] provinces;

    int[] provincesItemsRes = {
            R.array.beijinProvinceItem,
            R.array.tianjinProvinceItem,
            R.array.hebeiProvinceItem,
            R.array.shanxi1ProvinceItem,
            R.array.neimengguProvinceItem,
            R.array.liaoningProvinceItem,
            R.array.jilinProvinceItem,
            R.array.heilongjiangProvinceItem,
            R.array.shanghaiProvinceItem,
            R.array.jiangsuProvinceItem,
            R.array.zhejiangProvinceItem,
            R.array.anhuiProvinceItem,
            R.array.fujianProvinceItem,
            R.array.jiangxiProvinceItem,
            R.array.shandongProvinceItem,
            R.array.henanProvinceItem,
            R.array.hubeiProvinceItem,
            R.array.hunanProvinceItem,
            R.array.guangdongProvinceItem,
            R.array.guangxiProvinceItem,
            R.array.hainanProvinceItem,
            R.array.chongqingProvinceItem,
            R.array.sichuanProvinceItem,
            R.array.guizhouProvinceItem,
            R.array.yunnanProvinceItem,
            R.array.xizangProvinceItem,
            R.array.shanxi2ProvinceItem,
            R.array.gansuProvinceItem,
            R.array.qinghaiProvinceItem,
            R.array.ningxiaProvinceItem,
            R.array.xinjiangProvinceItem,
            R.array.hongkongProvinceItem,
            R.array.aomenProvinceItem,
            R.array.taiwanProvinceItem
    };


    @Click
    void areaItemClicked(View view) {
        MaterialDialog dialog = new MaterialDialog.Builder(this).theme(Theme.DARK).title(getString(R.string.integrateInfoAreaHint))
                .positiveText(R.string.integrateInfoChooseBtn).customView(R.layout.dialog_area_pick).build();
        View dialogView = dialog.getCustomView();
        Spinner provinceSpinner = (Spinner) dialogView.findViewById(R.id.provinceSpinner);
        Spinner districtSpinner = (Spinner) dialogView.findViewById(R.id.districtSpinner);

        districtSpinner.setEnabled(false);
        provinceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, provinces));
        provinceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                districtSpinner.setEnabled(true);
                districtSpinner.setAdapter(new ArrayAdapter<>(IntegrateInfoActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        getResources().getStringArray(provincesItemsRes[position])));
                districtSpinner.setSelection(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                districtSpinner.setEnabled(false);
            }
        });
        provinceSpinner.setSelection(0);
        dialog.setOnDismissListener(dialog1 -> {
            String province = (String) provinceSpinner.getSelectedItem();
            String district = (String) districtSpinner.getSelectedItem();
            setProvinceAndDistrict(province, district);
        });
        dialog.show();
    }

    @ViewById
    TextView areaTextView;

    private String mProvince;
    private String mDistrict;

    @AfterViews
    void initAreaTextView() {
        areaTextView.setText(provinces[0] + getResources().getStringArray(provincesItemsRes[0])[0]);
    }

    private void setProvinceAndDistrict(String pro, String dis) {
        mProvince = pro;
        mDistrict = dis;
        areaTextView.setText(pro + dis);
    }


    public static final int MALE = 0;
    public static final int FEMALE = 1;
    public static final int Private = 2;

    @ViewById
    TextView genderTextView;

    private final String[] gender = {"m", "f", "n"};
    private int genderSelected = MALE;

    private void setGender(int gender) {
        genderSelected = gender;
        switch (gender) {
            case MALE:
                genderTextView.setText(String.valueOf('\u2642'));
                break;
            case FEMALE:
                genderTextView.setText(String.valueOf('\u2640'));
                break;
            case Private:
                genderTextView.setText(getString(R.string.integrateInfoGenderPrivate));
            default:
                LogUtil.e(TAG, "unknown gender!!");
                break;
        }
    }

    @Click
    void genderItemClicked(View view) {
        new MaterialDialog.Builder(this)
                .theme(Theme.DARK)
                .title(R.string.integrateInfoGenderHint)
                .items(R.array.integrateInfoGenderArray)
                .itemsCallbackSingleChoice(genderSelected, (dialog, view1, which, text) -> {
                    setGender(which);
                    return true; // allow selection
                })
                .positiveText(R.string.integrateInfoChooseBtn)
                .show();
    }

    @Click
    void okBtnClicked(View view) {
        String nickname = String.valueOf(nickNameEditText.getText());
        if (TextUtils.isEmpty(nickname)) {
            showNotification(R.string.integrateInfoNameEmpty);
        } else {
            showProgress();
            ((IdentityInfo.Update) (new IdentityInfo.Update().with(this)))
                    .setGender(gender[genderSelected])
                    .setLocation(mProvince + mDistrict)
                    .setNickname(nickname).setCallback((e, result) -> {
                if (e != null) {
                    e.printStackTrace();
                    showNotification("update identity info failed!");
                } else if (result.getCode() == ErrorCode.SUCCESS) {
                    AccountHelper.updateAccount(this, result.getData());
                    showNotification("success");
                    setResult(RESULT_OK);
                    this.finish();
                } else {
                    showNotification("update identity info failed!");
                }
                hideProgress();
            });
        }
    }


}