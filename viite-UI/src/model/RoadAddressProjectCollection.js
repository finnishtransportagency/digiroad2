(function(root) {
  root.RoadAddressProjectCollection = function(backend) {
  var roadAddressProjects = [];

    this.getAll = function() {
      return backend.getRoadAddressProjects(function(projects){
        roadAddressProjects = projects;
      });
    };

    //TODO getAllProjectsInASpecificState
    this.getByState = function(state) {

    };

    this.clearRoadAddressProjects = function(){
      roadAddressProjects = [];
    };

    this.createProject = function(data){
      var dataJson = {name : data[0].value, startDate: data[1].value , additionalInfo :  data[2].value, roadNumber : data[3].value === '' ? 0 : parseInt(data[3].value), startPart: data[4].value === '' ? 0 : parseInt(data[4].value), endPart : data[5].value === '' ? 0 : parseInt(data[5].value) };
      backend.createRoadAddressProject(dataJson, function() {
        eventbus.trigger('roadaddress:projectSaved');
      }, function() {
        eventbus.trigger('roadaddress:projectFailed');
      });
    };

  };
})(this);
