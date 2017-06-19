(function(root) {
  root.ProjectChangeInfoModel = function(backend) {

    var projectChanges;


    function getChanges(projectID){
      backend.getChangeTable(projectID,function(changedata) {
        projectChanges= roadChangeAPIResultParser(changedata);
      });
    }

    function roadChangeAPIResultParser(projectChanges) {
      //TODO: Parse
      return projectChanges;
    }

    return{
      roadChangeAPIResultParser: roadChangeAPIResultParser,
      getChanges: getChanges
    };
  };
})(this);