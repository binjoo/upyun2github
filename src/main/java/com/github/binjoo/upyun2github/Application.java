package com.github.binjoo.upyun2github;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.binjoo.upyun2github.bean.FileInfoBean;
import com.github.binjoo.upyun2github.utils.MD5Utils;
import com.upyun.RestManager;
import com.upyun.UpException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@Slf4j
public class Application implements CommandLineRunner {
    private final String prefix = "INPUT_UPYUN_";
    private String dir = null;
    private String bucket = null;
    private String username = null;
    private String password = null;
    private RestManager manager = null;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        dir = System.getenv(prefix + "DIR");
        if (dir == null) {
            dir = "./";
        }
        bucket = System.getenv(prefix + "BUCKET");
        username = System.getenv(prefix + "USERNAME");
        password = System.getenv(prefix + "PASSWORD");
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("bucket or username or password is null");
        }

        File githubPath = new File(dir);
        List<FileInfoBean> githubFiles = this.findGithubFile(githubPath);

        this.manager = new RestManager(bucket, username, password);
        this.manager.setApiDomain(RestManager.ED_AUTO);
        List<FileInfoBean> upyunFiles = this.findUpyunFile("/");

        List<String> same = new ArrayList<>();// 相同，没变化
        List<String> modify = new ArrayList<>();// 相同，有变化
        List<String> delete = new ArrayList<>(); // 要删除的
        List<String> add = new ArrayList<>();// 要增加的

        log.info(" === start github to upyun compare === ");
        log.info(" === github file num: {}", githubFiles.size());
        for (int i = 0; i < githubFiles.size(); i++) {
            FileInfoBean githubFile = githubFiles.get(i);

            if (i % 100 == 0) {
                log.info(" === github files progress : {} / {}", i, githubFiles.size());
            }

            boolean hasDel = true; // 表示在github中存在，upyun不存在，要删除的
            for (int j = 0; j < upyunFiles.size(); j++) {
                FileInfoBean upyunFile = upyunFiles.get(j);
                if (githubFile.getPathName().equals(upyunFile.getPathName())) {
                    if (githubFile.getMd5().equals(upyunFile.getMd5())) {
                        same.add(githubFile.getPathName());
                    } else {
                        modify.add(githubFile.getPathName());
                    }
                    hasDel = false;    // 不用删除
                    break;
                }
            }
            if (hasDel) {
                // 删除关联不到的
                delete.add(githubFile.getPathName());
            }
        }
        log.info(" === github files progress : {} / {}, done.", githubFiles.size(), githubFiles.size());
        log.info(" === end github to upyun compare === ");
        System.out.println();

        log.info(" === start upyun to github compare === ");
        log.info(" === upyun file num: {}", upyunFiles.size());
        for (int i = 0; i < upyunFiles.size(); i++) {
            FileInfoBean upyunFile = upyunFiles.get(i);

            if (i % 100 == 0) {
                log.info(" === upyun files progress : {} / {}", i, upyunFiles.size());
            }

            boolean hasAdd = true;
            for (int j = 0; j < githubFiles.size(); j++) {
                FileInfoBean githubFile = githubFiles.get(j);
                if (githubFile.getPathName().equals(upyunFile.getPathName())) {
                    hasAdd = false;
                    break;
                }
            }
            if (hasAdd) {
                add.add(upyunFile.getPathName());
            }
        }
        log.info(" === upyun files progress : {} / {}, done.", upyunFiles.size(), upyunFiles.size());
        log.info(" === end upyun to github compare === ");
        System.out.println();

        log.info(" === start delete file === ");
        log.info(" === delete file num: {}", delete.size());
        for (int i = 0; i < delete.size(); i++) {
            File file = new File(dir + delete.get(i));
            if (file.exists() && file.isFile()) {
                file.deleteOnExit();
            }
            if (i % 100 == 0) {
                log.info(" === delete files progress : {} / {}", i, delete.size());
            }
        }
        log.info(" === delete files progress : {} / {}, done.", delete.size(), delete.size());
        log.info(" === end delete file === ");
        System.out.println();

        log.info(" === start download file === ");
        List<String> downloadPath = new ArrayList<>();
        downloadPath.addAll(modify);
        downloadPath.addAll(add);

        log.info(" === download file num: {}", downloadPath.size());
        for (int i = 0; i < downloadPath.size(); i++) {
            this.fetch(downloadPath.get(i));
            if (i % 100 == 0) {
                log.info(" === download files progress : {} / {}", i, downloadPath.size());
            }
        }
        log.info(" === download files progress : {} / {}, done.", downloadPath.size(), downloadPath.size());
        log.info(" === end download file === ");
        System.out.println();

        log.info(" === start result === ");
        this.console(same, " === 相同：{}");
        this.console(modify, " === 修改：{}");
        this.console(delete, " === 删除：{}");
        this.console(add, " === 新增：{}");
        log.info(" === end result === ");
    }

    private void console(List<String> list, String title) {
        log.info(title, list.size());
    }

    private List<FileInfoBean> findGithubFile(File dir) {
        List<FileInfoBean> files = new ArrayList<>();
        File[] lists = dir.listFiles();
        for (int i = 0; i < lists.length; i++) {
            if (lists[i].getName().indexOf(".") == 0) {
                continue;
            }
            if (lists[i].isFile()) {
                String githubName = lists[i].getAbsolutePath().substring(this.dir.length()).replace("\\", "/");
                FileInfoBean fileInfoBean = new FileInfoBean();
                fileInfoBean.setPathName(githubName);
                fileInfoBean.setMd5(MD5Utils.encrypt(lists[i]));
                files.add(fileInfoBean);
            } else {
                files.addAll(this.findGithubFile(lists[i]));
            }
        }
        return files;
    }

    private List<FileInfoBean> findUpyunFile(String dir) throws UpException, IOException {
        List<FileInfoBean> datas = new ArrayList<>();
        Map<String, String> params = new HashMap<>();
        params.put("Accept", "application/json");
        Response response = this.manager.readDirIter(dir, params);

        if (response != null) {
            JSONObject resBody = JSONObject.parseObject(response.body().string());
            JSONArray files = resBody.getJSONArray("files");
            for (int i = 0; i < files.size(); i++) {
                JSONObject file = files.getJSONObject(i);
                if ("folder".equals(file.getString("type"))) {
                    datas.addAll(this.findUpyunFile(dir + file.getString("name") + "/"));
                } else {
                    FileInfoBean fileInfoBean = new FileInfoBean();
                    fileInfoBean.setPathName(dir + file.getString("name"));
                    fileInfoBean.setMd5(this.getFileInfo(dir + file.getString("name")));
                    datas.add(fileInfoBean);
                }
            }
        }
        return datas;
    }

    private String getFileInfo(String filePath) throws UpException, IOException {
        Response response = this.manager.getFileInfo(filePath);
        return response.header("Content-Md5");
    }

    private void fetch(String pathName) throws UpException, IOException {
        Response response = this.manager.readFile(pathName);
        InputStream inputStream = response.body().byteStream();
        File target = new File(dir + pathName);
        if (!target.getParentFile().exists()) {
            target.getParentFile().mkdirs();
        }
        FileOutputStream fileOutputStream = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[2048];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
            }
            fileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
