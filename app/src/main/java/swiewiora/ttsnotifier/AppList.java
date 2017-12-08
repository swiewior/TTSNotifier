package swiewiora.ttsnotifier;

import android.app.ListActivity;
import android.content.Context;
import android.database.DataSetObserver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class AppList extends ListActivity {

    private Adapter adapter;
    private OnListUpdateListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Common.init(this);
        //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        lv.setFastScrollEnabled(true);
        adapter = new Adapter();
        listener = new OnListUpdateListener() {
            @Override
            public void onListUpdated() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.setData(apps);
                    }
                });
            }
            @Override
            public void onUpdateCompleted() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                    }
                });
                listener = null;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setIgnore((App)adapter.getItem(position), IGNORE_TOGGLE);
                adapter.notifyDataSetChanged();
            }
        });
        defEnable = Common.getPrefs(this).getBoolean(KEY_DEFAULT_ENABLE, true);
        updateAppsList();
    }

    private interface OnListUpdateListener {
        void onListUpdated();
        void onUpdateCompleted();
    }

    private class Adapter extends BaseAdapter implements Filterable {
        private final LayoutInflater mInflater;
        private ArrayList<AppCompatPreferenceActivity> baseData;
        private ArrayList<AppCompatPreferenceActivity> adapterData;

        private Adapter() {
            mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            adapterData = new ArrayList<>();
            baseData = new ArrayList<>();
        }

        private void setData(ArrayList<AppCompatPreferenceActivity> list) {
            baseData.clear();
            baseData.addAll(list);
            refresh();
        }

        private void refresh() {
            adapterData.clear();
            adapterData.addAll(baseData);
            notifyDataSetChanged();
        }
        
        @Override
        public void registerDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public int getCount() {
            return adapterData.size();
        }

        @Override
        public Object getItem(int i) {
            return adapterData.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

//        @Override
//        public boolean hasStableIds() {
//            return false;
//        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = mInflater.inflate(R.layout.app_list_item, viewGroup, false);
            }
            ((TextView)view.findViewById(R.id.text1)).setText(adapterData.get(i).getLabel());
            ((TextView)view.findViewById(R.id.text2)).setText(adapterData.get(i).getPackage());
            ((CheckBox)view.findViewById(R.id.checkbox)).setChecked(adapterData.get(i).getEnabled());
            return view;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Filter getFilter() {
            return null;
        }
    };
    }

}
