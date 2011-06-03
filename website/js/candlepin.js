function positionFooter() {
  var $footer = $('#footer');
  $footer.removeClass('fixed');
  if ($(document.body).height() < $(window).height()) {
    $footer.addClass('fixed');
  } else {
    $footer.removeClass('fixed');
  }
}

$(document).ready(function () {
  $(window).resize(positionFooter).resize();
  //preventFOUT();
});

$(window).ready(function () {
  //run _after_ images have been loaded as well.
  $(window).resize(positionFooter).resize();
});
