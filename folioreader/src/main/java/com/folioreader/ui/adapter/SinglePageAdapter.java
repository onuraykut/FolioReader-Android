package com.folioreader.ui.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import com.folioreader.ui.fragment.FolioPageFragment;
import org.readium.r2.shared.Link;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that displays a single fragment containing all merged chapters
 */
public class SinglePageAdapter extends FragmentStatePagerAdapter {

    private static final String LOG_TAG = SinglePageAdapter.class.getSimpleName();
    private List<Link> mSpineReferences;
    private String mEpubFileName;
    private String mBookId;
    private FolioPageFragment fragment;

    public SinglePageAdapter(FragmentManager fragmentManager, List<Link> spineReferences,
                            String epubFileName, String bookId) {
        super(fragmentManager);
        this.mSpineReferences = spineReferences;
        this.mEpubFileName = epubFileName;
        this.mBookId = bookId;
    }

    @Override
    public Fragment getItem(int position) {
        if (position != 0) return null;

        if (fragment == null) {
            fragment = FolioPageFragment.newInstanceMerged(
                mEpubFileName,
                new ArrayList<>(mSpineReferences),
                mBookId
            );
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return 1; // Only one page containing all merged chapters
    }

    public FolioPageFragment getFragment() {
        return fragment;
    }
}

