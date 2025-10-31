package com.folioreader.ui.adapter;

import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import com.folioreader.ui.fragment.FolioPageFragment;
import org.readium.r2.shared.Link;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mahavir on 4/2/16.
 */
public class FolioPageFragmentAdapter extends FragmentStatePagerAdapter {

    private static final String LOG_TAG = FolioPageFragmentAdapter.class.getSimpleName();
    private List<Link> mSpineReferences;
    private String mEpubFileName;
    private String mBookId;
    private ArrayList<Fragment> fragments;
    private ArrayList<Fragment.SavedState> savedStateList;

    public FolioPageFragmentAdapter(FragmentManager fragmentManager, List<Link> spineReferences,
                                    String epubFileName, String bookId) {
        super(fragmentManager);
        this.mSpineReferences = spineReferences;
        this.mEpubFileName = epubFileName;
        this.mBookId = bookId;
        // Only need one fragment now since we're merging all chapters
        fragments = new ArrayList<>(Arrays.asList(new Fragment[1]));
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);
        if (position == 0) {
            fragments.set(0, null);
        }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        if (position == 0) {
            fragments.set(0, fragment);
        }
        return fragment;
    }

    @Override
    public Fragment getItem(int position) {

        if (mSpineReferences.size() == 0)
            return null;

        // Always return the single merged fragment
        Fragment fragment = fragments.get(0);
        if (fragment == null) {
            fragment = FolioPageFragment.newInstanceMerged(
                    mEpubFileName, mSpineReferences, mBookId);
            fragments.set(0, fragment);
        }
        return fragment;
    }

    public ArrayList<Fragment> getFragments() {
        return fragments;
    }

    public ArrayList<Fragment.SavedState> getSavedStateList() {

        if (savedStateList == null) {
            try {
                Field field = FragmentStatePagerAdapter.class.getDeclaredField("mSavedState");
                field.setAccessible(true);
                savedStateList = (ArrayList<Fragment.SavedState>) field.get(this);
            } catch (Exception e) {
                Log.e(LOG_TAG, "-> ", e);
            }
        }

        return savedStateList;
    }

    public static Bundle getBundleFromSavedState(Fragment.SavedState savedState) {

        Bundle bundle = null;
        try {
            Field field = Fragment.SavedState.class.getDeclaredField("mState");
            field.setAccessible(true);
            bundle = (Bundle) field.get(savedState);
        } catch (Exception e) {
            Log.v(LOG_TAG, "-> " + e);
        }
        return bundle;
    }

    @Override
    public int getCount() {
        // Return 1 since we have only one merged page
        return mSpineReferences.size() > 0 ? 1 : 0;
    }
}
