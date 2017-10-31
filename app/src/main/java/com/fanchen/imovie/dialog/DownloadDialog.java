package com.fanchen.imovie.dialog;

import android.os.Environment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import com.arialyy.aria.core.Aria;
import com.fanchen.imovie.R;
import com.fanchen.imovie.adapter.EpisodeAdapter;
import com.fanchen.imovie.base.BaseActivity;
import com.fanchen.imovie.base.BaseAdapter;
import com.fanchen.imovie.entity.face.IPlayUrls;
import com.fanchen.imovie.entity.face.IVideoDetails;
import com.fanchen.imovie.entity.face.IVideoEpisode;
import com.fanchen.imovie.retrofit.RetrofitManager;
import com.fanchen.imovie.retrofit.callback.RefreshCallback;
import com.fanchen.imovie.util.DialogUtil;

import java.io.File;
import java.util.List;

/**
 * Created by fanchen on 2017/10/8.
 */
public class DownloadDialog extends BottomBaseDialog<DownloadDialog> implements
        BaseAdapter.OnItemClickListener, View.OnClickListener, RefreshCallback<IPlayUrls>{

    private RecyclerView mRecyclerView;
    private Button mAllButton;
    private Button mDownButton;

    private BaseActivity activity;
    private File mDownloadDir;
    private IVideoDetails mVideoDetails;
    private IVideoEpisode mDownload;
    private List<IVideoEpisode> mDownloads;
    private EpisodeAdapter mEpisodeAdapter;
    private RetrofitManager mRetrofitManager;

    public DownloadDialog(BaseActivity activity, IVideoDetails mVideoDetails) {
        super(activity);
        this.activity = activity;
        this.mVideoDetails = mVideoDetails;
        mRetrofitManager = RetrofitManager.with(context);
        mEpisodeAdapter = new EpisodeAdapter(context, false, true);
        mEpisodeAdapter.addAll(mVideoDetails.getEpisodes());
        mDownloadDir = new File(Environment.getExternalStorageDirectory() + "/android/data/" + context.getPackageName() + "/download/video/");
        if (!mDownloadDir.exists()) mDownloadDir.mkdirs();
    }

    @Override
    public View onCreateView() {
        View inflate = View.inflate(getContext(), R.layout.dialog_download, null);
        mRecyclerView = (RecyclerView) inflate.findViewById(R.id.rv_download_list);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        mRecyclerView.setAdapter(mEpisodeAdapter);
        mAllButton = (Button) inflate.findViewById(R.id.bt_download_all);
        mDownButton = (Button) inflate.findViewById(R.id.bt_download_selete);
        return inflate;
    }

    @Override
    public void setUiBeforShow() {
        mAllButton.setOnClickListener(this);
        mDownButton.setOnClickListener(this);
        mEpisodeAdapter.setOnItemClickListener(this);
        setOnDismissListener((OnDismissListener) activity);
    }

    @Override
    public void onItemClick(List<?> datas, View v, int position) {
        if (datas == null || datas.size() <= position) return;
        IVideoEpisode videoEpisode = (IVideoEpisode) datas.get(position);
        if (videoEpisode.getDownloadState() == IVideoEpisode.DOWNLOAD_SELECT) {
            videoEpisode.setDownloadState(IVideoEpisode.DOWNLOAD_NON);
        } else if (videoEpisode.getDownloadState() == IVideoEpisode.DOWNLOAD_NON) {
            videoEpisode.setDownloadState(IVideoEpisode.DOWNLOAD_SELECT);
        }
        mEpisodeAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        if (mEpisodeAdapter == null) return;
        switch (v.getId()) {
            case R.id.bt_download_all:
                mAllButton.setText(mEpisodeAdapter.isSelectAll() ? "取消全选" : "全部选中");
                mEpisodeAdapter.setSelectAll(!mEpisodeAdapter.isSelectAll());
                break;
            case R.id.bt_download_selete:
                mDownloads = mEpisodeAdapter.getSelect();
                if (mDownloads == null || mDownloads.size() == 0) {
                    activity.showToast(R.string.error_download_non);
                } else {
                    DialogUtil.showProgressDialog(activity,activity.getString(R.string.download_ing));
                    download(mDownloads.remove(0));
                }
                break;
        }
    }

    private void download(IVideoEpisode select) {
        this.mDownload = select;
        if (IVideoEpisode.PLAY_TYPE_VIDEO == select.getPlayerType()) {
            String fileNmae = mVideoDetails.getTitle() + "_" + select.getTitle() + ".mp4";
            Aria.download(activity.appliction).load(select.getUrl()).setDownloadPath(mDownloadDir + fileNmae).start();
            mDownload.setDownloadState(IVideoEpisode.DOWNLOAD_RUN);
            onFinish(-1);
        } else if (IVideoEpisode.PLAY_TYPE_URL == select.getPlayerType()) {
            try {
                String[] split = select.getId().split("\\?");
                if (split.length == 2) {
                    mRetrofitManager.enqueue(select.getServiceClassName(), this, "playUrl", split[0], split[1].replace("link=", ""));
                } else {
                    mRetrofitManager.enqueue(select.getServiceClassName(), this, "playUrl", split[0]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            DialogUtil.closeProgressDialog();
            activity.showToast(R.string.error_download_type);
        }
    }

    @Override
    public void onStart(int enqueueKey) {
    }

    @Override
    public void onFailure(int enqueueKey, String throwable) {
    }

    @Override
    public void onFinish(int enqueueKey) {
        if(mEpisodeAdapter != null)
            mEpisodeAdapter.notifyDataSetChanged();
        if (mDownloads != null && mDownloads.size() > 0) {
            download(mDownloads.remove(0));
        } else {
            dismiss();
            DialogUtil.closeProgressDialog();
        }
    }

    @Override
    public void onSuccess(int enqueueKey, IPlayUrls response) {
        Aria.download(this).stopAllTask();
        if (mDownload != null && response != null && response.getPlayType() == IVideoEpisode.PLAY_TYPE_VIDEO && response.getUrls() != null && !response.getUrls().isEmpty()
                && !TextUtils.isEmpty(response.getUrls().entrySet().iterator().next().getValue())) {
            String value = response.getUrls().entrySet().iterator().next().getValue();
            String fileNmae = mVideoDetails.getTitle() + "_" + mDownload.getTitle() + ".mp4";
            Aria.download(activity.appliction).load(value).setDownloadPath(mDownloadDir + fileNmae).start();
            mDownload.setDownloadState(IVideoEpisode.DOWNLOAD_RUN);
        }
    }

}