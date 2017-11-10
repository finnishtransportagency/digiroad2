(function(root) {
  root.ProjectChangeInfoModel = function(backend) {

    var roadInfoList=[{endAddressM:1,endRoadPartNumber:0,roadNumber:0,startAddressM:0,startRoadPartNumber:0,trackCode:0}];
    var changesInfo=[{changetype:0,discontinuity:"jatkuva",roadType:9,source:roadInfoList,target:roadInfoList,reversed: false}];
    var projectChanges={id:0,name:"templateproject", user:"templateuser",ely:0,changeDate:"1980-01-28",changeInfoSeq:changesInfo};



    function getChanges(projectID){
      $('.project-changes').html('<table class="change-table"></table>');
      backend.getChangeTable(projectID,function(changedata) {
        var parsedResult=roadChangeAPIResultParser(changedata);
        if (parsedResult!==null && parsedResult.discontinuity !==null) {
          eventbus.trigger('projectChanges:fetched', roadChangeAPIResultParser(parsedResult));
        }
      });
    }

    function roadChangeAPIResultParser(changeData) {
      projectChanges=changeData;
      return projectChanges;
    }

    return{
      roadChangeAPIResultParser: roadChangeAPIResultParser,
      getChanges: getChanges
    };
  };
})(this);