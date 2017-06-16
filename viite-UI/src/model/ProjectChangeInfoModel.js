(function(root) {
  root.ProjectChangeInfoModel = function(backend) {



  function roadChangeAPIResultParser(projectID){
    backend.getChangeTable(projectID,function(changedata) {
    var result=changedata;
  }
  );

    }
    return{
      roadChangeAPIResultParser: roadChangeAPIResultParser
    };
  };
})(this);