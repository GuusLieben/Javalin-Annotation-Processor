package nl.guuslieben.javalinproc;

import com.google.gson.Gson;

import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.javalin.Javalin;
import io.javalin.core.security.AccessManager;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class JavalinProc {

    private static final Javalin APP = Javalin.create(cfg -> {
        cfg.defaultContentType = "application/json";
        cfg.enableCorsForAllOrigins();
        cfg.showJavalinBanner = false;
    });

    private static final Logger LOG = LoggerFactory.getLogger(JavalinProc.class);

    public static void init(int port, AccessManager manager, Object... objs) {
        setupAuth(manager);
        init(port, objs);
    }

    public static void init(int port, Object... objs) {
        for (Object obj : objs) {
            if (obj instanceof String) handleEndpointsInPackage((String) obj);
            else handleEndpointsInPackage(obj.getClass().getPackageName());
        }
        APP.start(port);
    }

    private static void setupAuth(AccessManager accessManager) {
        APP.config.accessManager(accessManager);
    }

    private static void handleEndpointsInPackage(String pkg) {
        Set<Method> epMtds = new Reflections(pkg, new MethodAnnotationsScanner()).getMethodsAnnotatedWith(Endpoint.class);
        epMtds.forEach(mtd -> {
            Endpoint endpoint = mtd.getAnnotation(Endpoint.class);
            String path = endpoint.value();
            Class<?> par = mtd.getDeclaringClass();
            Object parI;

            Endpoint pref;
            if ((pref = mtd.getDeclaringClass().getAnnotation(Endpoint.class)) != null) {
                String pathPre = pref.value();
                if (pathPre.endsWith("/")) pathPre = pathPre.substring(0, pathPre.length() - 1);
                path = pathPre + path;
            }

            try {
                parI = par.newInstance();
                if (mtd.getParameterCount() > 1)
                    throw new IllegalArgumentException(String.format("Method contained more parameters than allowed. Was : %d, expected 1", mtd.getParameterCount()));

                if (mtd.getReturnType().equals(Void.TYPE)) {
                    if (!mtd.getParameterTypes()[0].equals(Context.class))
                        throw new IllegalArgumentException(String.format("First argument of void method was not of type %s", Context.class.getCanonicalName()));

                    handleVoidMethod(endpoint, mtd, parI, path);

                } else {
                    if (mtd.getParameterCount() > 0 && !mtd.getParameterTypes()[0].equals(Context.class))
                        throw new IllegalArgumentException(String.format("First argument of non-void method was not of type %s", Context.class.getCanonicalName()));

                    handleNonVoidMethod(endpoint, mtd, parI, path);
                }
            } catch (InstantiationException | IllegalAccessException e) {
                LOG.error(String.format("Could not prepare endpoint '%s' with method '%s'%n", path, endpoint.method()));
                e.printStackTrace();
            }
        });
    }

    private static void handleNonVoidMethod(Endpoint endpoint, Method mtd, Object parI, String path) {
        registerHandler(endpoint, ctx -> {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                Object res = null;
                try {
                    if (mtd.getParameterCount() == 0) {
                        res = mtd.invoke(parI);
                    } else res = mtd.invoke(parI, ctx);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                if (res == null) res = new EmptyResponse();

                Class<?>[] defaultTypes = new Class<?>[] {String.class, byte[].class, InputStream.class, CompletableFuture.class};
                Object finalRes = res;
                if (Arrays.stream(defaultTypes).noneMatch(type -> finalRes.getClass().isAssignableFrom(type))) res = new Gson().toJson(res);
                return res;
            });

            ctx.result(future);
        }, path);
    }

    private static void handleVoidMethod(Endpoint endpoint, Method mtd, Object parI, String path) {
        registerHandler(endpoint, ctx -> mtd.invoke(parI, ctx), path);
    }

    private static void registerHandler(Endpoint endpoint, Handler handler, String path) {
        switch (endpoint.method()) {
            case POST:
                APP.post(path, handler);
                break;
            case PUT:
                APP.put(path, handler);
                break;
            case PATCH:
                APP.patch(path, handler);
                break;
            case DELETE:
                APP.delete(path, handler);
                break;
            case HEAD:
                APP.head(path, handler);
                break;
            case OPTIONS:
                APP.options(path, handler);
                break;
            case BEFORE:
                APP.before(path, handler);
                break;
            case AFTER:
                APP.after(path, handler);
                break;
            case GET:
            default:
                APP.get(path, handler);
                break;
        }
        LOG.info(String.format("Registered %s %s", endpoint.method(), path));
    }

    private static class EmptyResponse {
        String response = "";
    }

}
