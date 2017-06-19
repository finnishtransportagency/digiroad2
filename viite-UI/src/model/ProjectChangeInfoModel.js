(function(root) {
  root.ProjectChangeInfoModel = function(backend) {

    var sourceList=[{endAddressM:1,endRoadPartNumber:0,roadNumber:0,startAddressM:0,startRoadPartNumber:0,trackCode:0}];
    var targetList=[{endAddressM:1,endRoadPartNumber:0,roadNumber:0,startAddressM:0,startRoadPartNumber:0,trackCode:0}];
    var changesInfo=[{changetype:0,discontinuity:"jatkuva",roadType:9,source:sourceList,target:targetList}];
    var projectChanges={id:0,name:"templateproject", user:"templateuser",ely:0,changeDate:"1980-01-28",changeInfoSeq:changesInfo};



    function getChanges(projectID){
      backend.getChangeTable(projectID,function(changedata) {
        projectChanges= roadChangeAPIResultParser(changedata);
        eventbus.trigger('projectChanges:fetched', projectChanges);
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