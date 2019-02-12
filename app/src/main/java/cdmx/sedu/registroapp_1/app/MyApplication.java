package cdmx.sedu.registroapp_1.app;

import android.app.Application;

import java.util.concurrent.atomic.AtomicInteger;

import cdmx.sedu.registroapp_1.models.Actividades;
import cdmx.sedu.registroapp_1.models.Usuario;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.RealmConfiguration.Builder;

/**
 * @author NumPoet
 */

public class MyApplication extends Application {

    public static AtomicInteger UsuarioID = new AtomicInteger();
    public static AtomicInteger ActividadesID = new AtomicInteger();

    @Override
    public void onCreate() {
        super.onCreate();
        setUpRealmConfig();

        Realm realm = Realm.getDefaultInstance();
        UsuarioID = getIdByTable(realm,  Usuario.class);
        ActividadesID = getIdByTable(realm, Actividades.class);
        realm.close();

    }

    private void setUpRealmConfig(){
        RealmConfiguration config = new RealmConfiguration
                .Builder()
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);

    }

    private <T extends RealmObject> AtomicInteger getIdByTable(Realm realm, Class<T> anyClass){
        RealmResults<T> results = realm.where(anyClass).findAll();
        return (results.size() > 0) ? new AtomicInteger(results.max("id").intValue()) : new AtomicInteger();
    }
}
