(function (root) {
  root.CsvBackend = function() {
    var self = this;

    this.uploadFile = function(formData, assetType, success, failure) {
      $.ajax({
        url: 'api/import/' + assetType,
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        success: success,
        error: failure
      });
    };

    this.getJobs = function() {
      return $.getJSON('api/import/log');
    };

    this.getJob = function(id) {
      return $.getJSON('api/import/log/' + id);
    };

    this.getJobsByIds = function(ids) {
      return $.getJSON('api/import/logs/' + ids);
    };
  };
}(this));
