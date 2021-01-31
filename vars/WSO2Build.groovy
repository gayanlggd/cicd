def call() {

    Boolean deployToProduction = env.TAG_NAME || false
    if(deployToProduction == true){
        ProdBuild()
    }else{
        DevBuild()
    }
    
}
