package cdmx.sedu.registroapp_1.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import cdmx.sedu.registroapp_1.R;
import cdmx.sedu.registroapp_1.app.MyApplication;
import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by Dev Ahmed Qeshta on 25/5/2018
 * email : dev.ahmed.m@gmail.com
 * phone : +970597503338
 */

public class MyService extends Service {
    String base_url = MyApplication.base_url;
    Realm realm;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Realm.init(this);
        realm = Realm.getDefaultInstance();

        // Update & Delete & Add Data Synchronization with Mysql
        // Get Data from Realm DB
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull final Realm realm) {
                RealmResults<Phone> result = realm.where(Phone.class)
                        .findAll();
                if (result.size() > 0) {
                    for (final Phone phone : result) {
                        final int id = phone.getId();
                        final int q = phone.getQuantity();
                        final Double p = phone.getPrice();
                        final String t = phone.getType();
                        final byte[] img = phone.getImage();
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(img, 0, img.length);
                        final Date date_m = phone.getDate_modified();

                        /* if found Data need Synchronization
                        id = -id        >>> Update
                        quantity = -1   >>> Delete
                        id = 0          >>> Add
                         */

                        // Synchronization Update
                        if (id < 0) {
                            Toast.makeText(getApplicationContext(), "Synchronization Update", Toast.LENGTH_LONG).show();
                            updateAsync(Math.abs(id), q, p, t, bitmap, date_m);
                        }

                        // Synchronization Delete
                        else if (q == -1) {
                            Toast.makeText(getApplicationContext(), "Synchronization Delete", Toast.LENGTH_LONG).show();
                            deleteAsync(id);
                        }

                        // Synchronization Add
                        else if (id == 0) {
                            Toast.makeText(getApplicationContext(), "Synchronization Add", Toast.LENGTH_LONG).show();
                            addAsync(id, q, p, t, bitmap, date_m);
                        }

                        // No Synchronization
                        else
                            Toast.makeText(getApplicationContext(), "No Data Synchronization", Toast.LENGTH_LONG).show();
                    }

                    // No Data found to Synchronization so Get All Data from Mysql
                } else
                    Toast.makeText(getApplicationContext(), "No Data found to Synchronization", Toast.LENGTH_LONG).show();
            }
        });

        getAllData();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "get");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Notes");
        builder.setContentText("my message");
        Notification note = builder.build();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.notify(1, note);
        Log.i("tag", "Notifying ...");

        return START_STICKY;
    }

    private void updateAsync(final int id, final int q, final Double p, final String t, final Bitmap bitmap, final Date date_app) {
        // Get data by id & Update from Mysql (Online)
        String url_api_locate = base_url + "API/api_locate.php?id=" + id;
        JsonArrayRequest stringRequest = new JsonArrayRequest(Request.Method.GET, url_api_locate, null,
                new Response.Listener<JSONArray>() {
                    @SuppressLint("SimpleDateFormat")
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        try {
                            if (!jsonArray.isNull(0)) {
                                final JSONObject obj = jsonArray.getJSONObject(0);
                                @SuppressLint("SimpleDateFormat") DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                                Date date_web = dateFormat.parse(obj.getString("date_modified"));
                                // Compare by data modified then if true Update from Mysql (Online)
                                if (date_app.after(date_web)) {
                                    String url_api_edit = base_url + "API/api_edit.php";
                                    StringRequest stringRequest = new StringRequest(Request.Method.POST, url_api_edit, new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String s) {
                                            try {
                                                JSONObject jsonObject = new JSONObject(s);
                                                // Update.... response
                                                Toast.makeText(getApplicationContext(), jsonObject.getString("response"), Toast.LENGTH_LONG).show();
                                                if (jsonObject.getString("response").equals("Update Successfully")) {
                                                    // Delete from Realm (Offline) local DB
                                                    realm.executeTransaction(new Realm.Transaction() {
                                                        @Override
                                                        public void execute(@NonNull Realm realm) {
                                                            RealmResults<Phone> result = realm.where(Phone.class).equalTo("id", -id)
                                                                    .findAll();
                                                            result.deleteAllFromRealm();
                                                        }
                                                    });
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }, new Response.ErrorListener() {
                                        @SuppressLint("SetTextI18n")
                                        @Override
                                        public void onErrorResponse(VolleyError volleyError) {
                                            Toast.makeText(getApplicationContext(), "Error in connection", Toast.LENGTH_LONG).show();
                                        }
                                    }) {
                                        @Override
                                        protected Map<String, String> getParams() {
                                            Map<String, String> map = new HashMap<>();
                                            map.put("id", String.valueOf(id));
                                            map.put("quantity", String.valueOf(q));
                                            map.put("price", String.valueOf(p));
                                            map.put("type", String.valueOf(t));
                                            map.put("image", imageToString(bitmap));
                                            return map;
                                        }
                                    };
                                    MySingleton.getInstance(getApplicationContext()).addToResquestQueue(stringRequest);
                                } else if (date_web.after(date_app)) {
                                    Toast.makeText(getApplicationContext(), "NO Update old Data", Toast.LENGTH_LONG).show();
                                    // Delete from Realm (Offline) local DB
                                    realm.executeTransaction(new Realm.Transaction() {
                                        @Override
                                        public void execute(@NonNull Realm realm) {
                                            RealmResults<Phone> result = realm.where(Phone.class).equalTo("id", -id)
                                                    .findAll();
                                            result.deleteAllFromRealm();
                                        }
                                    });
                                }
                            }
                        } catch (JSONException | ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                System.out.println(error.getMessage());
            }
        });
        MySingleton.getInstance(getApplicationContext()).addToResquestQueue(stringRequest);
    }

    private void addAsync(final int id, final int q, final Double p, final String t, final Bitmap bitmap, final Date date_app) {
        // Add from Mysql (Online)
        String url_api_add = base_url + "API/api_add.php";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url_api_add, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    Toast.makeText(getApplicationContext(), jsonObject.getString("response"), Toast.LENGTH_LONG).show();
                    if (jsonObject.getString("response").equals("Add Successfully")) {
                        // Delete from Realm (Offline) local DB
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                RealmResults<Phone> result = realm.where(Phone.class).equalTo("id", id)
                                        .findAll();
                                if (result.size() > 0) {
                                    for (final Phone phone : result) {
                                        if (phone.getDate_modified().equals(date_app))
                                            phone.deleteFromRealm();
                                    }
                                }
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Toast.makeText(getApplicationContext(), "Error in connection", Toast.LENGTH_LONG).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> map = new HashMap<>();
                map.put("quantity", String.valueOf(q));
                map.put("price", String.valueOf(p));
                map.put("type", String.valueOf(t));
                map.put("image", imageToString(bitmap));
                return map;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToResquestQueue(stringRequest);
    }

    private void deleteAsync(final int id) {
        // Delete from Mysql (Online)
        String url_api_delete = base_url + "API/api_delete.php?id=" + id;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url_api_delete, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                try {
                    Toast.makeText(getApplicationContext(), jsonObject.getString("response"), Toast.LENGTH_LONG).show();
                    if (jsonObject.getString("response").equals("Delete Successfully")) {
                        // Delete from Realm (Offline) local DB
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                RealmResults<Phone> result = realm.where(Phone.class).equalTo("id", id)
                                        .findAll();
                                result.deleteAllFromRealm();
                            }
                        });
                    } else if (jsonObject.getString("response").equals("Failed to Delete")) {
                        // Delete from Realm (Offline) local DB
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                RealmResults<Phone> result = realm.where(Phone.class).equalTo("id", id)
                                        .findAll();
                                result.deleteAllFromRealm();
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Toast.makeText(getApplicationContext(), "Error in connection", Toast.LENGTH_LONG).show();
            }
        });
        MySingleton.getInstance(getApplicationContext()).addToResquestQueue(jsonObjectRequest);
    }

    private void getAllData() {
        // Get Images and Data and Delete old data from Realm database....
        String url_api_get = base_url + "API/api_get.php";
        JsonArrayRequest stringRequest = new JsonArrayRequest(url_api_get,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        if (!jsonArray.isNull(0)) {
                            // Delete old data from database....
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(@NonNull Realm realm) {
                                    realm.delete(Phone.class);
                                }
                            });
                        }
                        for (int i = 0; i < jsonArray.length(); i++) {
                            try {
                                final JSONObject obj = jsonArray.getJSONObject(i);
                                // Get Data and save on Realm DB
                                String url = base_url + "img/" + obj.getString("image");
                                ImageRequest imageRequest = new ImageRequest(url, new Response.Listener<Bitmap>() {
                                    @Override
                                    public void onResponse(final Bitmap bitmap) {
                                        realm.executeTransactionAsync(new Realm.Transaction() {
                                            @Override
                                            public void execute(@NonNull Realm realm) {
                                                try {
                                                    Phone phone = realm.createObject(Phone.class);
                                                    // Get Data and save on Realm DB
                                                    phone.setId(obj.getInt("id"));
                                                    phone.setQuantity(obj.getInt("quantity"));
                                                    phone.setPrice(obj.getDouble("price"));
                                                    phone.setType(obj.getString("type"));
                                                    phone.setImageName(obj.getString("image"));
                                                    String date_modified = obj.getString("date_modified");
                                                    @SuppressLint("SimpleDateFormat") SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                                    phone.setDate_modified(dateParser.parse(date_modified));
                                                    // Get Images and save on Realm DB
                                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                                    byte[] byteArray = stream.toByteArray();
                                                    phone.setImage(byteArray);

                                                } catch (JSONException | ParseException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, new Realm.Transaction.OnSuccess() {
                                            @Override
                                            public void onSuccess() {
                                                Toast.makeText(getApplicationContext(), "Get Data", Toast.LENGTH_LONG).show();
                                            }
                                        }, new Realm.Transaction.OnError() {
                                            @Override
                                            public void onError(@NonNull Throwable error) {
                                                Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        });

                                    }
                                }, 0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565, new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError volleyError) {
                                        Log.e("e", volleyError.getMessage());
                                    }
                                });
                                MySingleton.getInstance(getApplicationContext()).addToResquestQueue(imageRequest);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                System.out.println(error.getMessage());
            }
        });
        MySingleton.getInstance(getApplicationContext()).addToResquestQueue(stringRequest);
    }

    private String imageToString(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imgBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imgBytes, Base64.DEFAULT);
    }

}
