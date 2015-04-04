package co.yishun.onemoment.app.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import co.yishun.onemoment.app.R;
import co.yishun.onemoment.app.config.Constants;
import co.yishun.onemoment.app.config.ErrorCode;
import co.yishun.onemoment.app.net.request.account.IdentityInfo;
import co.yishun.onemoment.app.net.result.AccountResult;
import co.yishun.onemoment.app.util.AccountHelper;
import co.yishun.onemoment.app.util.LogUtil;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.squareup.picasso.Picasso;
import org.androidannotations.annotations.*;
import org.androidannotations.annotations.res.StringArrayRes;

import java.util.Arrays;

@EActivity(R.layout.activity_identity_info)
public class IdentityInfoActivity extends ToolbarBaseActivity {
    public static final int REQUEST_UPDATE_INFO = 100;
    public static final int REQUEST_IMAGE_SELECT = 101;
    public static final int REQUEST_IMAGE_CROP = 102;
    private static final String TAG = LogUtil.makeTag(IdentityInfoActivity.class);
    @ViewById
    ImageView profileImageView;
    @ViewById
    TextView nickNameTextView;
    @ViewById
    TextView weiboTextView;
    @ViewById
    TextView genderTextView;
    @ViewById
    TextView areaTextView;

    @AfterViews
    void initViews() {
        AccountResult.Data data = AccountHelper.getIdentityInfo(this);
        Picasso.with(this).load(data.getAvatar_url()).into(profileImageView);
        nickNameTextView.setText(data.getNickname());
        weiboTextView.setText(data.getWeibo_uid() == null ? R.string.identityInfoWeiboUnbound : R.string.identityInfoWeiboBound);
        setGender(data.getGender());
        setProvinceAndDistrict(data.getArea());
    }

    public static final int MALE = 0;
    public static final int FEMALE = 1;
    public static final int PRIVATE = 2;
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
            case PRIVATE:
                genderTextView.setText(getString(R.string.integrateInfoGenderPrivate));
            default:
                LogUtil.e(TAG, "unknown gender!!");
                break;
        }
    }

    private void setGender(String gender) {
        int genderInt = gender.indexOf(gender.trim());
        genderSelected = genderInt;
        setGender(genderInt);
    }

    @Click
    void profileItemClicked(View view) {
        //TODO profile
        startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_IMAGE_SELECT);
    }

    @OnActivityResult(REQUEST_IMAGE_SELECT)
    @Background
    void onPictureSelected(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String picturePath = cursor.getString(columnIndex);
                cursor.close();

                setProfileImage(selectedImage);

//                Intent gallery_Intent = new Intent(this, GalleryUtil.class);
//                startActivityForResult(gallery_Intent, REQUEST_IMAGE_CROP);
            } catch (Exception e) {
                e.printStackTrace();
                showNotification(R.string.identityInfoSelectProfileFail);
            }
        } else {
            LogUtil.i(TAG, "RESULT_CANCELED");
        }
    }

    void setProfileImage(Uri uri) {
        Picasso.with(this).load(uri).into(profileImageView);
    }

    @Click
    void nickNameItemClicked(View view) {
        final View customView = LayoutInflater.from(this).inflate(R.layout.dialog_nickname, null);
        final EditText edit = (EditText) customView.findViewById(R.id.nickNameEditText);
        final String oldName = nickNameTextView.getText().toString().trim();
        edit.setText(oldName);
        new MaterialDialog.Builder(this).theme(Theme.DARK).customView(customView, false)
                .title(R.string.integrateInfoNickNameHint)
                .positiveText(R.string.identityInfoOk)
                .negativeText(R.string.identityInfoCancel)
                .autoDismiss(false).callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                String newName = edit.getText().toString().trim();
                if (!AccountHelper.isValidNickname(newName)) {
                    showNotification(R.string.identityInfoNicknameInvalid);
                    YoYo.with(Techniques.Shake).duration(700).playOn(edit);
                } else if (!oldName.equals(newName)) {
                    updateInfo("nickname", newName, nickNameTextView);
                    dialog.dismiss();
                } else {
                    dialog.dismiss();
                }
            }

            @Override
            public void onNegative(MaterialDialog dialog) {
                super.onNegative(dialog);
                dialog.dismiss();
            }
        }).show();
    }

    @Click
    void genderItemClicked(View view) {
        new MaterialDialog.Builder(this)
                .theme(Theme.DARK)
                .title(R.string.integrateInfoGenderHint)
                .items(R.array.integrateInfoGenderArray)
                .itemsCallbackSingleChoice(genderSelected, (dialog, view1, which, text) -> {
                    updateGender(which);
                    return true; // allow selection
                }).positiveText(R.string.integrateInfoChooseBtn)
                .show();
    }

    @Background
    void updateGender(int which) {
        showProgress();
        ((IdentityInfo.Update) (new IdentityInfo.Update().with(this)))
                .setGender(gender[which]).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
                showNotification(R.string.identityInfoUpdateFail);
            } else switch (result.getCode()) {
                case ErrorCode.SUCCESS:
                    AccountHelper.updateAccount(this, result.getData());
                    showNotification(R.string.identityInfoUpdateSuccess);
                    runOnUiThread(() -> setGender(which));
                    break;
                case ErrorCode.GENDER_FORMAT_ERROR:
                    LogUtil.e(TAG, "GENDER_FORMAT_ERROR");
                    break;
                default:
                    showNotification(R.string.identityInfoUpdateFail);
                    break;
            }
            hideProgress();
        });
    }

    @StringArrayRes
    String[] provinces;
    private String mProvince;
    private String mDistrict;

    private void setProvinceAndDistrict(String pro, String dis) {
        mProvince = pro;
        mDistrict = dis;
        areaTextView.setText(pro + dis);
    }

    private void setProvinceAndDistrict(String provinceAndDistrict) {
        if (provinceAndDistrict == null || provinceAndDistrict.length() < 2) {
            areaTextView.setText(provinceAndDistrict);
            return;
        }
        String twoChar = provinceAndDistrict.substring(0, 2);
        for (String province : provinces) {
            if (province.startsWith(twoChar)) mProvince = province;
        }
        mDistrict = provinceAndDistrict.substring(mProvince.length());
        areaTextView.setText(provinceAndDistrict);
    }


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
                String[] districts = getResources().getStringArray(Constants.provincesItemsRes[position]);
                districtSpinner.setEnabled(true);
                districtSpinner.setAdapter(new ArrayAdapter<>(IdentityInfoActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, districts));
                int selected = Arrays.asList(districts).indexOf(mDistrict);
                districtSpinner.setSelection(selected >= 0 ? selected : 0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                districtSpinner.setEnabled(false);
            }
        });
        int selected = Arrays.asList(provinces).indexOf(mProvince);
        provinceSpinner.setSelection(selected >= 0 ? selected : 0);
        dialog.setOnDismissListener(dialog1 -> {
            String province = (String) provinceSpinner.getSelectedItem();
            String district = (String) districtSpinner.getSelectedItem();
            if (!province.equals(mProvince) || !district.equals(mDistrict))
                updateArea(province, district);
        });
        dialog.show();
    }

    @Background
    void updateArea(@NonNull String pro, @NonNull String dis) {
        showProgress();
        ((IdentityInfo.Update) (new IdentityInfo.Update().with(this)))
                .setLocation(pro + dis).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
                showNotification(R.string.identityInfoUpdateFail);
            } else switch (result.getCode()) {
                case ErrorCode.SUCCESS:
                    AccountHelper.updateAccount(this, result.getData());
                    showNotification(R.string.identityInfoUpdateSuccess);
                    runOnUiThread(() -> setProvinceAndDistrict(pro, dis));
                    break;
                case ErrorCode.LOCATION_FORMAT_ERROR:
                    LogUtil.e(TAG, "LOCATION_FORMAT_ERROR");
                    break;
                default:
                    showNotification(R.string.identityInfoUpdateFail);
                    break;
            }
            hideProgress();
        });
    }

    @Background
    void updateInfo(String key, String value, TextView showValueView) {
        showProgress();
        ((IdentityInfo.Update) (new IdentityInfo.Update().with(this)))
                .set(key, value).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
                showNotification(R.string.identityInfoUpdateFail);
            } else switch (result.getCode()) {
                case ErrorCode.SUCCESS:
                    AccountHelper.updateAccount(this, result.getData());
                    showNotification(R.string.identityInfoUpdateSuccess);
                    runOnUiThread(() -> showValueView.setText(value));
                    break;
                case ErrorCode.NICKNAME_EXISTS:
                    showNotification(R.string.identityInfoNicknameExist);
                    break;
                case ErrorCode.NICKNAME_FORMAT_ERROR:
                    showNotification(R.string.identityInfoNicknameNetworkInvalid);
                    break;
                case ErrorCode.LOCATION_FORMAT_ERROR:
                    LogUtil.e(TAG, "LOCATION_FORMAT_ERROR");
                    break;
                //TODO add more
                default:
                    showNotification(R.string.identityInfoUpdateFail);
                    break;
            }
            hideProgress();
        });
    }

    @Click
    void weiboItemClicked(View view) {
        //TODO handle weibo
    }

    @Click(R.id.logoutBtn)
    void logout(View view) {
        new MaterialDialog.Builder(this).theme(Theme.DARK).title(R.string.identityInfoLogout).content(R.string.identityInfoLogoutAlert)
                .positiveText(R.string.identityInfoLogout).negativeText(R.string.cancel).callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onNegative(MaterialDialog dialog) {
                super.onNegative(dialog);
            }

            @Override
            public void onPositive(MaterialDialog dialog) {
                super.onPositive(dialog);
                AccountHelper.deleteAccount(IdentityInfoActivity.this);
                IdentityInfoActivity.this.finish();
            }
        }).show();
    }
}
