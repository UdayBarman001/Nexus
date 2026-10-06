'use client';

import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useDropzone, FileRejection } from 'react-dropzone';
import {
  UploadCloud,
  FileText,
  Download,
  Trash2,
  CheckCircle2,
  AlertCircle,
  Clock,
  Loader2,
  Search,
  LayoutGrid,
  Table as TableIcon,
  Filter,
  Layers,
  FileCheck,
  ChevronLeft,
  ChevronRight,
  Database,
  X,
} from 'lucide-react';
import {
  getDocumentsApi,
  getDocumentStatsApi,
  uploadDocumentApi,
  downloadDocumentApi,
  deleteDocumentApi,
} from '@/api/documents';
import { DocumentItem, DocumentProcessingStatus } from '@/types';
import { cn, formatBytes, formatDate } from '@/lib/utils';
import { toast } from 'sonner';

export default function DocumentsPage() {
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [statusFilter, setStatusFilter] = useState<DocumentProcessingStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'table' | 'grid'>('table');
  const [documentToDelete, setDocumentToDelete] = useState<DocumentItem | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  // TanStack Query with dynamic polling: polls every 3s if any doc is PENDING or PROCESSING
  const { data, isLoading } = useQuery({
    queryKey: ['documents', page, pageSize, statusFilter],
    queryFn: () =>
      getDocumentsApi(
        page,
        pageSize,
        statusFilter === 'ALL' ? undefined : statusFilter
      ),
    refetchInterval: (query) => {
      const docs = query.state.data?.content || [];
      const hasActiveJobs = docs.some(
        (d) => d.processingStatus === 'PENDING' || d.processingStatus === 'PROCESSING'
      );
      return hasActiveJobs ? 3000 : false;
    },
  });

  const hasActiveJobs = (data?.content || []).some(
    (d) => d.processingStatus === 'PENDING' || d.processingStatus === 'PROCESSING'
  );

  // Document statistics from dedicated backend endpoint
  const { data: statsData } = useQuery({
    queryKey: ['documentStats'],
    queryFn: getDocumentStatsApi,
    refetchInterval: hasActiveJobs ? 3000 : false,
  });


  // Upload mutation
  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadDocumentApi(file),
    onSuccess: (newDoc) => {
      setIsUploading(false);
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['documentStats'] });
      toast.success(`"${newDoc.filename}" uploaded and queued for vectorization!`);
    },
    onError: (err: any) => {
      setIsUploading(false);
      const msg = err.response?.data?.message || 'Failed to upload document';
      toast.error(msg);
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteDocumentApi(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['documentStats'] });
      setDocumentToDelete(null);
      toast.success('Document deleted successfully');
    },
    onError: (err: any) => {
      const msg = err.response?.data?.message || 'Failed to delete document';
      toast.error(msg);
    },
  });

  // react-dropzone configuration
  const onDrop = useCallback(
    (acceptedFiles: File[], fileRejections: FileRejection[]) => {
      if (fileRejections && fileRejections.length > 0) {
        fileRejections.forEach((rejection) => {
          const fileName = rejection.file.name;
          const reasons = rejection.errors.map((e) => {
            if (e.code === 'file-too-large') {
              return 'File size exceeds maximum limit of 50MB';
            }
            if (e.code === 'file-invalid-type') {
              return 'Unsupported file format (PDF, DOCX, TXT only)';
            }
            return e.message;
          });
          toast.error(`${fileName}: ${reasons.join(', ')}`);
        });
      }

      if (acceptedFiles.length === 0) return;
      const file = acceptedFiles[0];
      if (file.size > 50 * 1024 * 1024) {
        toast.error('File size exceeds maximum limit of 50MB');
        return;
      }
      setIsUploading(true);
      uploadMutation.mutate(file);
    },
    [uploadMutation]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: false,
    accept: {
      'application/pdf': ['.pdf'],
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
      'text/plain': ['.txt'],
    },
    maxSize: 50 * 1024 * 1024,
  });

  // Download handler
  const handleDownload = async (doc: DocumentItem) => {
    try {
      toast.info(`Downloading ${doc.filename}...`);
      await downloadDocumentApi(doc.id, doc.filename);
    } catch (err) {
      toast.error('Failed to download document');
    }
  };

  // Filter list by local search query
  const documents = (data?.content || []).filter((d) =>
    d.filename.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Summary statistics from backend stats endpoint
  const totalDocs = statsData?.totalDocuments ?? (data?.totalElements || 0);
  const embeddedDocs =
    statsData?.embeddedDocuments ??
    (data?.content || []).filter((d) => d.processingStatus === 'EMBEDDED').length;
  const totalChunks =
    statsData?.totalChunks ??
    (data?.content || []).reduce((acc, cur) => acc + (cur.chunkCount || 0), 0);

  const renderStatusBadge = (status: DocumentProcessingStatus, failureReason?: string | null) => {
    switch (status) {
      case 'EMBEDDED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-950/60 text-emerald-400 border border-emerald-500/30">
            <CheckCircle2 className="w-3.5 h-3.5" />
            Embedded
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-indigo-950/60 text-indigo-400 border border-indigo-500/30">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            Processing
          </span>
        );
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-950/60 text-amber-400 border border-amber-500/30">
            <Clock className="w-3.5 h-3.5" />
            Pending
          </span>
        );
      case 'FAILED':
        return (
          <span
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-950/60 text-rose-400 border border-rose-500/30 cursor-help"
            title={failureReason || 'Embedding pipeline failed'}
          >
            <AlertCircle className="w-3.5 h-3.5" />
            Failed
          </span>
        );
    }
  };

  return (
    <div className="p-4 sm:p-8 max-w-7xl mx-auto space-y-6">
      {/* Header & Stats Ribbon */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Database className="w-7 h-7 text-purple-400" />
            Document Vault
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Upload enterprise documents to vectorize and ground your AI knowledge base.
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-3 gap-3">
          <div className="glass-card px-4 py-2.5 rounded-2xl border border-slate-800/80">
            <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
              Total Files
            </div>
            <div className="text-lg font-bold text-white mt-0.5">{totalDocs}</div>
          </div>
          <div className="glass-card px-4 py-2.5 rounded-2xl border border-slate-800/80">
            <div className="text-[10px] uppercase font-bold text-emerald-400 tracking-wider">
              Embedded
            </div>
            <div className="text-lg font-bold text-emerald-300 mt-0.5">{embeddedDocs}</div>
          </div>
          <div className="glass-card px-4 py-2.5 rounded-2xl border border-slate-800/80">
            <div className="text-[10px] uppercase font-bold text-indigo-400 tracking-wider">
              Vector Chunks
            </div>
            <div className="text-lg font-bold text-indigo-300 mt-0.5">{totalChunks}</div>
          </div>
        </div>
      </div>

      {/* Drag-and-Drop Upload Zone */}
      <div
        {...getRootProps()}
        className={cn(
          'relative border-2 border-dashed rounded-3xl p-8 text-center cursor-pointer transition-all duration-300 backdrop-blur-xl',
          isDragActive
            ? 'border-indigo-500 bg-indigo-950/20 scale-[1.01]'
            : 'border-slate-800 bg-slate-900/30 hover:border-slate-700 hover:bg-slate-900/50'
        )}
      >
        <input {...getInputProps()} />
        <div className="flex flex-col items-center justify-center gap-3">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-purple-600/30 to-indigo-600/30 border border-purple-500/30 flex items-center justify-center text-purple-300 shadow-lg shadow-purple-500/10">
            {isUploading ? (
              <Loader2 className="w-7 h-7 animate-spin text-indigo-400" />
            ) : (
              <UploadCloud className="w-7 h-7" />
            )}
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-200">
              {isDragActive
                ? 'Drop your document here...'
                : isUploading
                ? 'Uploading & vectorizing document...'
                : 'Click or drag & drop documents here'}
            </p>
            <p className="text-xs text-slate-400 mt-1">
              Supports <span className="text-slate-300 font-medium">PDF, DOCX, TXT</span> up to 50MB
            </p>
          </div>
        </div>
      </div>

      {/* Controls Bar: Search, Status Filter, View Toggle */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2">
        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Search box */}
          <div className="relative flex-1 sm:w-72">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              placeholder="Search documents..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-xs bg-slate-900/90 border border-slate-800 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500/70 transition-colors"
            />
          </div>

          {/* Status Filter Dropdown */}
          <div className="relative">
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as any);
                setPage(0);
              }}
              className="appearance-none bg-slate-900/90 border border-slate-800 rounded-xl px-3 py-2 pr-8 text-xs text-slate-300 focus:outline-none focus:border-indigo-500/70 transition-colors cursor-pointer"
            >
              <option value="ALL">All Statuses</option>
              <option value="EMBEDDED">Embedded</option>
              <option value="PROCESSING">Processing</option>
              <option value="PENDING">Pending</option>
              <option value="FAILED">Failed</option>
            </select>
            <Filter className="w-3.5 h-3.5 text-slate-500 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>

        {/* View Mode Toggle */}
        <div className="flex items-center gap-1 bg-slate-900/90 border border-slate-800 p-1 rounded-xl">
          <button
            onClick={() => setViewMode('table')}
            className={cn(
              'p-1.5 rounded-lg transition-colors',
              viewMode === 'table' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
            )}
            title="Table View"
          >
            <TableIcon className="w-4 h-4" />
          </button>
          <button
            onClick={() => setViewMode('grid')}
            className={cn(
              'p-1.5 rounded-lg transition-colors',
              viewMode === 'grid' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
            )}
            title="Grid View"
          >
            <LayoutGrid className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Documents Content */}
      {isLoading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-slate-500">
          <Loader2 className="w-7 h-7 animate-spin text-purple-400" />
          <span className="text-xs">Fetching document catalog...</span>
        </div>
      ) : documents.length === 0 ? (
        <div className="glass-card rounded-3xl p-12 text-center border border-slate-800/80">
          <FileText className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-slate-300">No documents found</h3>
          <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
            {searchQuery
              ? 'No documents match your search criteria.'
              : 'Your vault is currently empty. Drop a file above to vectorize your knowledge!'}
          </p>
        </div>
      ) : viewMode === 'table' ? (
        /* Table View */
        <div className="glass-card rounded-3xl border border-slate-800/80 overflow-hidden shadow-xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-900/80 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3.5">Filename</th>
                  <th className="px-4 py-3.5">Status</th>
                  <th className="px-4 py-3.5">Size</th>
                  <th className="px-4 py-3.5">Chunks</th>
                  <th className="px-4 py-3.5">Uploaded</th>
                  <th className="px-5 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {documents.map((doc) => (
                  <tr
                    key={doc.id}
                    className="hover:bg-slate-900/40 transition-colors group"
                  >
                    <td className="px-5 py-3.5 font-medium text-slate-200">
                      <div className="flex items-center gap-2.5">
                        <FileText className="w-4 h-4 text-purple-400 shrink-0" />
                        <span className="truncate max-w-xs" title={doc.filename}>
                          {doc.filename}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      {renderStatusBadge(doc.processingStatus, doc.failureReason)}
                    </td>
                    <td className="px-4 py-3.5 text-slate-400">
                      {formatBytes(doc.fileSizeBytes)}
                    </td>
                    <td className="px-4 py-3.5">
                      <span className="font-semibold text-slate-200">
                        {doc.chunkCount || 0}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-400">
                      {formatDate(doc.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => handleDownload(doc)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-indigo-400 hover:bg-slate-800 transition-colors"
                          title="Download document"
                        >
                          <Download className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => setDocumentToDelete(doc)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
                          title="Delete document"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        /* Grid View */
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {documents.map((doc) => (
            <div
              key={doc.id}
              className="glass-card rounded-3xl p-5 border border-slate-800/80 hover:border-slate-700 transition-all shadow-md flex flex-col justify-between group"
            >
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="w-10 h-10 rounded-2xl bg-purple-950/60 border border-purple-500/30 flex items-center justify-center text-purple-400 shrink-0">
                    <FileText className="w-5 h-5" />
                  </div>
                  {renderStatusBadge(doc.processingStatus, doc.failureReason)}
                </div>

                <h4
                  className="font-semibold text-sm text-slate-200 truncate mb-1"
                  title={doc.filename}
                >
                  {doc.filename}
                </h4>

                <div className="flex items-center gap-3 text-xs text-slate-400 mt-2">
                  <span>{formatBytes(doc.fileSizeBytes)}</span>
                  <span>•</span>
                  <span>{doc.chunkCount || 0} chunks</span>
                  <span>•</span>
                  <span>{formatDate(doc.createdAt)}</span>
                </div>
              </div>

              <div className="pt-4 mt-4 border-t border-slate-800/80 flex items-center justify-end gap-2">
                <button
                  onClick={() => handleDownload(doc)}
                  className="p-2 rounded-xl text-slate-400 hover:text-indigo-400 hover:bg-slate-800 transition-colors"
                  title="Download"
                >
                  <Download className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setDocumentToDelete(doc)}
                  className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
                  title="Delete"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pagination Controls */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between pt-4 border-t border-slate-800/80 text-xs text-slate-400">
          <div>
            Showing Page <span className="font-semibold text-white">{data.number + 1}</span> of{' '}
            <span className="font-semibold text-white">{data.totalPages}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={data.first}
              className="p-2 rounded-xl border border-slate-800 hover:bg-slate-800/80 disabled:opacity-40 disabled:cursor-not-allowed text-slate-300 transition-colors"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={data.last}
              className="p-2 rounded-xl border border-slate-800 hover:bg-slate-800/80 disabled:opacity-40 disabled:cursor-not-allowed text-slate-300 transition-colors"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Confirmation Modal for Document Deletion */}
      {documentToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
          <div className="glass-card rounded-3xl p-6 max-w-md w-full border border-slate-800 shadow-2xl relative">
            <button
              onClick={() => setDocumentToDelete(null)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-white rounded-lg"
            >
              <X className="w-4 h-4" />
            </button>

            <div className="w-12 h-12 rounded-2xl bg-rose-950/60 border border-rose-500/30 flex items-center justify-center text-rose-400 mb-4">
              <Trash2 className="w-6 h-6" />
            </div>

            <h3 className="text-lg font-bold text-white mb-1">Delete Document?</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Are you sure you want to permanently delete{' '}
              <span className="font-semibold text-slate-200">
                &ldquo;{documentToDelete.filename}&rdquo;
              </span>
              ? Its vector embeddings and chunks will also be purged from the index.
            </p>

            <div className="flex items-center justify-end gap-2.5 mt-6">
              <button
                type="button"
                onClick={() => setDocumentToDelete(null)}
                className="px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800 rounded-xl transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => deleteMutation.mutate(documentToDelete.id)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 text-xs font-medium text-white bg-rose-600 hover:bg-rose-500 rounded-xl transition-colors flex items-center gap-1.5 disabled:opacity-50"
              >
                {deleteMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                <span>Delete</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
