# Runs once per data root. Stock atomic settings writer preserves other values.
.method private static xhynEnsureEnglish(Ljava/io/File;)V
    .locals 4

    :try_start
    new-instance v0, Ljava/io/File;
    const-string v1, "LibertyRecomp/.base-ui-english-v444"
    invoke-direct {v0, p0, v1}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
    invoke-virtual {v0}, Ljava/io/File;->isFile()Z
    move-result v1
    if-nez v1, :done

    const-string v1, "user_language"
    const-string v2, "0"
    invoke-static {v1, v2}, Ljava/util/Collections;->singletonMap(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;
    move-result-object v3
    invoke-static {p0, v3}, La/i;->J(Ljava/io/File;Ljava/util/Map;)V
    invoke-static {v0}, La/i;->S(Ljava/io/File;)V
    :try_end
    .catch Ljava/io/IOException; {:try_start .. :try_end} :failed
    :done
    return-void

    :failed
    move-exception v0
    const-string v1, "XHYN-English"
    const-string v2, "Could not set the initial game language to English"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
