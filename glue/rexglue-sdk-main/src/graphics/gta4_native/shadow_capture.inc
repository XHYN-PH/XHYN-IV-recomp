// Explicit, bounded observation-only image capture. Layouts are restored in
// the same command buffer; host reads wait for the owning slot's GPU fence.
void Gta4NativeGraphicsSystem::RecordShadowCapture(VkCommandBuffer cb,uint32_t frame,
    VkImage visible,VkImageLayout visible_layout,uint32_t width,uint32_t height,
    const EnvironmentalDataV2* environment) {
  if(!CachedDiagnosticsEnabled(rex::diagnostics::Category::kLogging)||!visible)return;
  const uint32_t request=rex::cvar::Query<uint32_t>("gta4_shadow_capture_trigger");
  if(request!=shadow_capture_request_){shadow_capture_request_=request;shadow_capture_remaining_=request?3:0;shadow_capture_last_frame_=0;shadow_capture_budget_=96ull<<20;}
  if(!shadow_capture_remaining_||(shadow_capture_last_frame_&&frame-shadow_capture_last_frame_<2))return;
  --shadow_capture_remaining_;shadow_capture_last_frame_=frame;
  const std::string log=rex::cvar::GetFlagByName("log_file");if(log.empty()||log=="/dev/null")return;
  const auto directory=std::filesystem::path(log+".shadow")/fmt::format("request-{}-frame-{}",request,frame);
  std::error_code error;std::filesystem::create_directories(directory,error);if(error)return;
  auto* device=static_cast<ui::vulkan::VulkanProvider*>(provider_.get())->vulkan_device();
  const auto& dfn=device->functions();
  std::ofstream manifest(directory/"frame.json");
  manifest.precision(9);
  manifest<<"{\"request\":"<<request<<",\"frame\":"<<frame<<",\"host_tick\":"<<rex::chrono::Clock::QueryHostTickCount()<<",\"host_frequency\":"<<rex::chrono::Clock::QueryHostTickFrequency()<<",\"images_are_observational\":true";
  if(environment){
    manifest<<",\"environment_valid_fields\":"<<environment->valid_fields;
    const auto array=[&](const char* name,const auto& values){manifest<<",\""<<name<<"\":[";bool first=true;for(float v:values){if(!first)manifest<<',';first=false;if(std::isfinite(v))manifest<<v;else manifest<<"null";}manifest<<']';};
    array("camera_position",environment->camera_position);array("view_matrix",environment->view_matrix);array("projection_matrix",environment->projection_matrix);array("view_projection_matrix",environment->view_projection_matrix);
  }
  manifest<<",\"capture_point\":\"end-of-native-frame\",\"surface_contents_need_matching_trace\":true,\"images\":[";bool first_image=true;
  const auto capture=[&](std::string role,VkImage image,VkFormat format,VkImageLayout layout,
      uint32_t w,uint32_t h,VkImageAspectFlagBits aspect,VkImageAspectFlags barrier_aspect,VkSampleCountFlagBits samples){
    uint32_t bpp=4;
    if(format==VK_FORMAT_R16G16B16A16_SFLOAT)bpp=8;
    const bool supported=format==VK_FORMAT_R8G8B8A8_UNORM||format==VK_FORMAT_R8G8B8A8_SRGB||format==VK_FORMAT_B8G8R8A8_UNORM||format==VK_FORMAT_B8G8R8A8_SRGB||format==VK_FORMAT_R32_SFLOAT||format==VK_FORMAT_D32_SFLOAT||format==VK_FORMAT_D32_SFLOAT_S8_UINT||format==VK_FORMAT_D24_UNORM_S8_UINT||format==VK_FORMAT_R16G16B16A16_SFLOAT;
    const uint64_t bytes=uint64_t(w)*h*bpp;
    std::string unavailable;
    if(!image||!w||!h||layout==VK_IMAGE_LAYOUT_UNDEFINED)unavailable="image-unavailable";
    else if(!supported)unavailable="unsupported-format";
    else if(samples!=VK_SAMPLE_COUNT_1_BIT)unavailable="multisample-source-needs-resolve";
    else if(uint64_t(w)*h>16777216||bytes>(64ull<<20)||bytes>shadow_capture_budget_)unavailable="capture-memory-budget";
    if(!first_image)manifest<<',';first_image=false;
    manifest<<"{\"role\":\""<<role<<"\",\"width\":"<<w<<",\"height\":"<<h<<",\"format\":"<<uint32_t(format)<<",\"image\":"<<NativeVulkanHandleIdentity(image);
    ShadowReadback p;p.width=w;p.height=h;p.format=format;p.aspect=aspect;p.frame=frame;p.request=request;p.bytes=bytes;p.directory=directory;p.role=role;
    VkDeviceSize allocation=0;
    if(unavailable.empty()&&!ui::vulkan::util::CreateDedicatedAllocationBuffer(device,bytes,VK_BUFFER_USAGE_TRANSFER_DST_BIT,ui::vulkan::util::MemoryPurpose::kReadback,p.buffer,p.memory,&p.memory_type,&allocation))unavailable="readback-allocation";
    if(unavailable.empty()&&dfn.vkMapMemory(device->device(),p.memory,0,VK_WHOLE_SIZE,0,reinterpret_cast<void**>(&p.mapping))!=VK_SUCCESS)unavailable="readback-map";
    if(!unavailable.empty()){
      if(p.buffer)dfn.vkDestroyBuffer(device->device(),p.buffer,nullptr);if(p.memory)dfn.vkFreeMemory(device->device(),p.memory,nullptr);
      manifest<<",\"status\":\""<<unavailable<<"\"}";return;
    }
    const auto sync=ImageLayoutSynchronization(layout,barrier_aspect);
    VkImageMemoryBarrier before{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};before.srcAccessMask=sync.access;before.dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;before.oldLayout=layout;before.newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;before.srcQueueFamilyIndex=before.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;before.image=image;before.subresourceRange={barrier_aspect,0,1,0,1};
    dfn.vkCmdPipelineBarrier(cb,sync.stages,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&before);
    VkBufferImageCopy copy{};copy.imageSubresource={aspect,0,0,1};copy.imageExtent={w,h,1};dfn.vkCmdCopyImageToBuffer(cb,image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,p.buffer,1,&copy);
    auto restore=before;restore.srcAccessMask=VK_ACCESS_TRANSFER_READ_BIT;restore.dstAccessMask=sync.access;restore.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;restore.newLayout=layout;
    VkBufferMemoryBarrier host{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};host.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;host.dstAccessMask=VK_ACCESS_HOST_READ_BIT;host.srcQueueFamilyIndex=host.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;host.buffer=p.buffer;host.size=bytes;
    dfn.vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TRANSFER_BIT,sync.stages|VK_PIPELINE_STAGE_HOST_BIT,0,0,nullptr,1,&host,1,&restore);
    shadow_capture_budget_-=bytes;shadow_readbacks_[active_frame_slot_].push_back(std::move(p));manifest<<",\"status\":\"gpu-copy-scheduled\"}";
  };
  capture("visible-frame",visible,ui::vulkan::VulkanPresenter::kGuestOutputFormat,visible_layout,width,height,VK_IMAGE_ASPECT_COLOR_BIT,VK_IMAGE_ASPECT_COLOR_BIT,VK_SAMPLE_COUNT_1_BIT);
  uint32_t depth_handle=0,color_handle=0;char separator=0;std::istringstream targets(rex::cvar::GetFlagByName("gta4_shadow_capture_targets"));targets>>depth_handle>>separator>>color_handle;
  NativeSurfaceImage* depth=nullptr;NativeSurfaceImage* color=nullptr;
  for(auto& image:native_surface_images_){if(!image)continue;if(image->descriptor.handle==depth_handle&&depth_handle)depth=image.get();if(image->descriptor.handle==color_handle&&color_handle)color=image.get();}
  const auto surface=[&](const char* role,NativeSurfaceImage* image,VkImageAspectFlagBits aspect){
    const bool initialized=image&&image->aspect_content.Has(aspect);
    capture(role,initialized?image->resource.image:VK_NULL_HANDLE,image?image->format:VK_FORMAT_UNDEFINED,image?image->layout:VK_IMAGE_LAYOUT_UNDEFINED,image?image->width:0,image?image->height:0,aspect,image?image->aspect:VkImageAspectFlags(aspect),image?image->samples:VK_SAMPLE_COUNT_1_BIT);
    if(image)MarkNativeSurfaceImageUsed(*image);
  };
  surface("directional-shadow-atlas-depth",depth,VK_IMAGE_ASPECT_DEPTH_BIT);surface("directional-shadow-color-filter-result",color,VK_IMAGE_ASPECT_COLOR_BIT);
  manifest<<"],\"surface_lineage\":[";
  bool first_surface=true;
  for(auto* image:{depth,color}){
    if(!image)continue;
    if(!first_surface)manifest<<',';first_surface=false;
    const bool is_depth=image==depth;
    const auto* owner=FindPlacementOwner(image->descriptor,is_depth);
    manifest<<"{\"handle\":"<<image->descriptor.handle<<",\"lifetime\":"<<image->lifetime_id
      <<",\"depth_writer\":"<<image->aspect_content.depth.writer_serial
      <<",\"color_writer\":"<<image->aspect_content.color.writer_serial
      <<",\"current_placement\":"<<(owner&&owner->image==image?"true":"false")
      <<",\"owner_frame\":"<<(owner?owner->frame:0)
      <<",\"owner_command\":"<<(owner?owner->command_index:SIZE_MAX)
      <<",\"owner_phase\":"<<(owner?uint32_t(owner->render_phase):0)<<'}';
  }
  manifest<<"],\"depth_surface_handle\":"<<depth_handle<<",\"color_surface_handle\":"<<color_handle<<",\"requested_frames_remaining\":"<<shadow_capture_remaining_<<"}\n";
  REXLOG_INFO("shadow-images: request={} frame={} slot={} copies={} budget_remaining={} (capture adds overhead; original layouts restored)",request,frame,active_frame_slot_,shadow_readbacks_[active_frame_slot_].size(),shadow_capture_budget_);
}

void Gta4NativeGraphicsSystem::PublishShadowCaptures(uint32_t slot,uint64_t submission){
  if(slot>=shadow_readbacks_.size())return;
  auto* device=static_cast<ui::vulkan::VulkanProvider*>(provider_.get())->vulkan_device();const auto& dfn=device->functions();
  auto& pending=shadow_readbacks_[slot];
  for(auto& p:pending){
    if(!p.submission||p.submission>submission)continue;
    bool ready=p.mapping!=nullptr;
    if(ready&&!(device->memory_types().host_coherent&(1u<<p.memory_type))){VkMappedMemoryRange range{VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE};range.memory=p.memory;range.size=VK_WHOLE_SIZE;ready=dfn.vkInvalidateMappedMemoryRanges(device->device(),1,&range)==VK_SUCCESS;}
    auto bytes=std::make_shared<std::vector<uint8_t>>();if(ready)bytes->assign(p.mapping,p.mapping+p.bytes);
    const auto meta=p;
    if(p.mapping)dfn.vkUnmapMemory(device->device(),p.memory);dfn.vkDestroyBuffer(device->device(),p.buffer,nullptr);dfn.vkFreeMemory(device->device(),p.memory,nullptr);p.buffer=VK_NULL_HANDLE;p.memory=VK_NULL_HANDLE;p.mapping=nullptr;
    if(!ready){REXLOG_WARN("shadow-images: frame={} role={} readback unavailable",p.frame,p.role);continue;}
    if(!EnqueueDeferredDiagnosticTask([meta,bytes]{
      std::vector<uint8_t> rgba(size_t(meta.width)*meta.height*4,255);
      const auto half=[](uint16_t h){const int exp=(h>>10)&31;const float sign=h&0x8000?-1.f:1.f;return exp==31?0.f:sign*std::ldexp(float((h&1023)+(exp?1024:0)),exp?exp-25:-24);};
      const auto channel=[](float v){return uint8_t(std::isfinite(v)?std::clamp(v,0.f,1.f)*255.f:0.f);};
      for(size_t i=0;i<size_t(meta.width)*meta.height;++i){
        auto* out=rgba.data()+i*4;
        if(meta.format==VK_FORMAT_R16G16B16A16_SFLOAT){for(size_t c=0;c<4;++c){uint16_t h;std::memcpy(&h,bytes->data()+i*8+c*2,2);out[c]=channel(half(h));}}
        else if(meta.aspect==VK_IMAGE_ASPECT_DEPTH_BIT||meta.format==VK_FORMAT_R32_SFLOAT){float value=0;if(meta.format==VK_FORMAT_D24_UNORM_S8_UINT){uint32_t packed;std::memcpy(&packed,bytes->data()+i*4,4);value=float(packed&0xFFFFFF)/16777215.f;}else std::memcpy(&value,bytes->data()+i*4,4);out[0]=out[1]=out[2]=channel(value);}
        else{std::memcpy(out,bytes->data()+i*4,4);if(meta.format==VK_FORMAT_B8G8R8A8_UNORM||meta.format==VK_FORMAT_B8G8R8A8_SRGB)std::swap(out[0],out[2]);}
        // Shadow targets often carry zero alpha. Keep the RGB visualization
        // visible in ordinary viewers; native alpha remains in the raw copy.
        out[3]=255;
      }
      const auto png=EncodeNativeCapturePng(meta.width,meta.height,rgba);const auto path=meta.directory/(meta.role+".png");
      {std::ofstream file(path.string()+".partial",std::ios::binary);file.write(reinterpret_cast<const char*>(png.data()),std::streamsize(png.size()));if(!file||png.empty())return;}
      std::error_code error;std::filesystem::rename(path.string()+".partial",path,error);if(error)return;
      std::vector<uint8_t> compressed(ZSTD_compressBound(bytes->size()));const size_t count=ZSTD_compress(compressed.data(),compressed.size(),bytes->data(),bytes->size(),1);
      bool raw_complete=false;
      if(!ZSTD_isError(count)){
        const auto raw_path=meta.directory/(meta.role+".bin.zst");
        {std::ofstream raw(raw_path.string()+".partial",std::ios::binary);raw.write(reinterpret_cast<const char*>(compressed.data()),std::streamsize(count));raw.flush();raw_complete=bool(raw);}
        if(raw_complete){std::filesystem::rename(raw_path.string()+".partial",raw_path,error);raw_complete=!error;}
      }
      if(!raw_complete)REXLOG_WARN("shadow-images: frame={} role={} raw export failed",meta.frame,meta.role);
      {std::ofstream status(meta.directory/(meta.role+".json"));status<<"{\"frame\":"<<meta.frame<<",\"request\":"<<meta.request<<",\"submission\":"<<meta.submission<<",\"width\":"<<meta.width<<",\"height\":"<<meta.height<<",\"format\":"<<uint32_t(meta.format)<<",\"png_complete\":true,\"raw_complete\":"<<(raw_complete?"true":"false")<<",\"png_description\":\"opaque RGB visualization clamped to 0..1; visible-frame is presenter input, not physical scanout; raw retains alpha\",\"raw_description\":\"lossless native-format tightly packed GPU copy when raw_complete is true\"}\n";}
      std::ofstream complete(meta.directory.parent_path()/"capture-complete.txt");complete<<"Completed images only. Each image has frame/submission metadata; frame manifests list unavailable or scheduled sources. Requests can end before all three frames are submitted.\n";
    }))REXLOG_WARN("shadow-images: frame={} role={} writer queue full",p.frame,p.role);
  }
  std::erase_if(pending,[](const auto& p){return !p.buffer;});
}

void Gta4NativeGraphicsSystem::DiscardUnsubmittedShadowCaptures(uint32_t slot){
  if(slot>=shadow_readbacks_.size()||!provider_)return;
  auto* device=static_cast<ui::vulkan::VulkanProvider*>(provider_.get())->vulkan_device();if(!device)return;
  const auto& dfn=device->functions();auto& pending=shadow_readbacks_[slot];
  for(auto& p:pending){
    if(p.submission)continue;
    if(p.mapping)dfn.vkUnmapMemory(device->device(),p.memory);
    if(p.buffer)dfn.vkDestroyBuffer(device->device(),p.buffer,nullptr);
    if(p.memory)dfn.vkFreeMemory(device->device(),p.memory,nullptr);
    p.buffer=VK_NULL_HANDLE;
  }
  std::erase_if(pending,[](const auto& p){return !p.buffer;});
}

void Gta4NativeGraphicsSystem::DestroyShadowCaptures(){
  auto* device=static_cast<ui::vulkan::VulkanProvider*>(provider_.get())->vulkan_device();if(!device)return;const auto& dfn=device->functions();
  for(auto& slot:shadow_readbacks_){for(auto& p:slot){if(p.mapping)dfn.vkUnmapMemory(device->device(),p.memory);if(p.buffer)dfn.vkDestroyBuffer(device->device(),p.buffer,nullptr);if(p.memory)dfn.vkFreeMemory(device->device(),p.memory,nullptr);}slot.clear();}
}
